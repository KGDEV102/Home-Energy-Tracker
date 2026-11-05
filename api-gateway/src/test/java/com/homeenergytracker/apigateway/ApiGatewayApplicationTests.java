package com.homeenergytracker.apigateway;

import com.homeenergytracker.apigateway.controller.GatewayFallbackController;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.cors.allowed-origins=http://localhost:5173, https://dev.vn",
        "GATEWAY_TIMEOUT=200ms",
        "GATEWAY_INSIGHT_TIMEOUT=200ms"
})
@AutoConfigureMockMvc
@AutoConfigureTestRestTemplate
class ApiGatewayApplicationTests {

    private static final ExecutorService downstreamExecutor =
            Executors.newFixedThreadPool(4);
    private static final HttpServer downstream = startDownstream();
    private static final AtomicInteger downstreamStatus =
            new AtomicInteger(200);

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(downstreamExecutor);
            server.createContext("/", exchange -> {
                if (exchange.getRequestURI().getPath().endsWith("/slow")) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                }
                String body = exchange.getRequestURI().toString() + "|"
                        + exchange.getRequestHeaders().getFirst("Authorization");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(downstreamStatus.get(), bytes.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        for (String property : new String[]{"USER_SERVICE_URL", "DEVICE_SERVICE_URL",
                "USAGE_SERVICE_URL", "NOTIFICATION_SERVICE_URL",
                "INSIGHT_SERVICE_URL"}) {
            registry.add(property,
                    () -> "http://localhost:" + downstream.getAddress().getPort());
        }
    }

    @AfterAll
    static void stopDownstream() {
        downstream.stop(0);
        downstreamExecutor.shutdownNow();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetDownstreamAndCircuitBreakers() {
        downstreamStatus.set(200);
        circuitBreakerRegistry.getAllCircuitBreakers()
                .forEach(CircuitBreaker::reset);
    }

    @Test
    void contextLoads() {
    }

    @Test
    void allowsPreflightForEachConfiguredOrigin() throws Exception {
        for (String origin : new String[]{"http://localhost:5173", "https://dev.vn"}) {
            mockMvc.perform(options("/api/example")
                            .header("Origin", origin)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        }
    }

    @Test
    void rejectsUnconfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/example")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void blocksInternalPaths() throws Exception {
        mockMvc.perform(get("/internal/example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void doesNotAuthenticateBearerTokensAtGateway() throws Exception {
        mockMvc.perform(get("/api/not-configured")
                        .header("Authorization", "Bearer example-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void forwardsConfiguredPathsAndBearerHeader() throws Exception {
        for (String path : new String[]{"/api/v1/auth/login", "/api/v1/users",
                "/api/v1/device", "/api/v1/device-models",
                "/api/v1/usages", "/api/v1/notification-rules",
                "/api/v1/notifications", "/api/v1/insights"}) {
            mockMvc.perform(get(path).queryParam("example", "1")
                            .header("Authorization", "Bearer example-token"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(path + "?example=1|Bearer example-token"));
        }
    }

    @Test
    void openCircuitShouldReturnFallbackForEveryService() throws Exception {
        Object[][] routes = {
                {"gatewayUserService", "/api/v1/users", "user-service"},
                {"gatewayDeviceService", "/api/v1/device", "device-service"},
                {"gatewayUsageService", "/api/v1/usages", "usage-service"},
                {"gatewayNotificationService", "/api/v1/notifications", "notification-service"},
                {"gatewayInsightService", "/api/v1/insights", "insight-service"}
        };

        for (Object[] route : routes) {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry
                    .circuitBreaker((String) route[0]);
            circuitBreaker.transitionToOpenState();

            ResponseEntity<GatewayFallbackController.GatewayFallbackResponse> response =
                    restTemplate.getForEntity(
                            (String) route[1],
                            GatewayFallbackController.GatewayFallbackResponse.class
                    );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isEqualTo(
                    new GatewayFallbackController.GatewayFallbackResponse(
                            false,
                            route[2] + " is temporarily unavailable"
                    )
            );
            circuitBreaker.reset();
        }
    }

    @Test
    void downstreamServerErrorShouldReturnFallback() throws Exception {
        downstreamStatus.set(501);

        ResponseEntity<GatewayFallbackController.GatewayFallbackResponse> response =
                restTemplate.getForEntity(
                        "/api/v1/users",
                        GatewayFallbackController.GatewayFallbackResponse.class
                );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo(
                new GatewayFallbackController.GatewayFallbackResponse(
                        false,
                        "user-service is temporarily unavailable"
                )
        );
    }

    @Test
    void downstreamTimeoutShouldReturnFallback() throws Exception {
        ResponseEntity<GatewayFallbackController.GatewayFallbackResponse> response =
                restTemplate.getForEntity(
                        "/api/v1/insights/slow",
                        GatewayFallbackController.GatewayFallbackResponse.class
                );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo(
                new GatewayFallbackController.GatewayFallbackResponse(
                        false,
                        "insight-service is temporarily unavailable"
                )
        );
    }

    @Test
    void fallbackEndpointShouldNotBeDirectlyAccessible() throws Exception {
        mockMvc.perform(get("/fallback/user-service"))
                .andExpect(status().isNotFound());
    }

}
