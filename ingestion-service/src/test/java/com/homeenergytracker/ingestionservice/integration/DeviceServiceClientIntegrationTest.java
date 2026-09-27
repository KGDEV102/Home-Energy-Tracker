package com.homeenergytracker.ingestionservice.integration;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.ingestionservice.client.DeviceServiceClient;
import com.homeenergytracker.ingestionservice.dto.DeviceSimulationTargetResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "simulation.refresh-initial-delay-ms=3600000",
        "simulation.job-initial-delay-ms=3600000"
})
@DirtiesContext
class DeviceServiceClientIntegrationTest {

    private static final String EXPECTED_PATH =
            "/internal/v1/devices/simulation-targets";

    private static final UUID DEVICE_UID =
            UUID.fromString("89fe1560-9507-4a5b-a1dc-37de87e9b50b");

    private static final String SUCCESS_BODY = """
            {
              "success": true,
              "data": [
                {
                  "deviceUid": "89fe1560-9507-4a5b-a1dc-37de87e9b50b",
                  "userId": 10,
                  "deviceType": "AIR_CONDITIONER",
                  "ratedPowerW": 1000
                }
              ],
              "message": "Operation Successful"
            }
            """;

    private static final AtomicReference<String> LAST_METHOD =
            new AtomicReference<>();

    private static final AtomicReference<String> LAST_PATH =
            new AtomicReference<>();

    private static final AtomicInteger RESPONSE_STATUS =
            new AtomicInteger(200);

    private static final AtomicReference<String> RESPONSE_BODY =
            new AtomicReference<>(SUCCESS_BODY);

    /*
     * Starts before Spring creates DeviceServiceClient, so the dynamic
     * base URL can contain the random port selected by the operating system.
     */
    private static final HttpServer HTTP_SERVER = startHttpServer();

    @Autowired
    private DeviceServiceClient deviceServiceClient;

    @DynamicPropertySource
    static void registerDeviceServiceBaseUrl(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.http.serviceclient.device-service.base-url",
                () -> "http://127.0.0.1:"
                        + HTTP_SERVER.getAddress().getPort()
        );
    }

    @BeforeEach
    void resetHttpResponse() {
        LAST_METHOD.set(null);
        LAST_PATH.set(null);
        RESPONSE_STATUS.set(200);
        RESPONSE_BODY.set(SUCCESS_BODY);
    }

    @AfterAll
    static void stopHttpServer() {
        HTTP_SERVER.stop(0);
    }

    @Test
    void getSimulationTargets_shouldCallExpectedEndpointAndMapResponse() {
        SuccessResponse<List<DeviceSimulationTargetResponse>> response =
                deviceServiceClient.getSimulationTargets();

        assertEquals("GET", LAST_METHOD.get());
        assertEquals(EXPECTED_PATH, LAST_PATH.get());

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals("Operation Successful", response.message());
        assertNotNull(response.data());
        assertEquals(1, response.data().size());

        DeviceSimulationTargetResponse device = response.data().getFirst();

        assertEquals(DEVICE_UID, device.deviceUid());
        assertEquals(10L, device.userId());
        assertEquals("AIR_CONDITIONER", device.deviceType());
        assertEquals(new BigDecimal("1000"), device.ratedPowerW());
    }

    @Test
    void getSimulationTargets_shouldThrowWhenDeviceServiceReturnsError() {
        RESPONSE_STATUS.set(503);
        RESPONSE_BODY.set("""
                {
                  "success": false,
                  "message": "Device service unavailable"
                }
                """);

        assertThrows(
                RestClientResponseException.class,
                deviceServiceClient::getSimulationTargets
        );

        assertEquals("GET", LAST_METHOD.get());
        assertEquals(EXPECTED_PATH, LAST_PATH.get());
    }

    private static HttpServer startHttpServer() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0),
                    0
            );

            server.createContext(EXPECTED_PATH, exchange -> {
                try {
                    handleRequest(exchange);
                } finally {
                    exchange.close();
                }
            });

            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not start HTTP test server",
                    exception
            );
        }
    }

    private static void handleRequest(HttpExchange exchange)
            throws IOException {
        LAST_METHOD.set(exchange.getRequestMethod());
        LAST_PATH.set(exchange.getRequestURI().getPath());

        byte[] body = RESPONSE_BODY.get()
                .getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json"
        );

        exchange.sendResponseHeaders(
                RESPONSE_STATUS.get(),
                body.length
        );

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
