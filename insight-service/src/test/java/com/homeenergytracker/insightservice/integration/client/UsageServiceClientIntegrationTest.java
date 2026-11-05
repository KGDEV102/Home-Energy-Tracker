package com.homeenergytracker.insightservice.integration.client;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.insightservice.client.UsageServiceClient;
import com.homeenergytracker.insightservice.dto.UsageSummaryResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UsageServiceClientIntegrationTest {

    private static final String BASE_URL = "http://usage-service.test";
    private static final Long USER_ID = 42L;
    private static final String INTERNAL_TOKEN = "insight-to-usage-token";
    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-02T00:00:00Z");

    private MockRestServiceServer server;
    private UsageServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder()
                .baseUrl(BASE_URL);

        server = MockRestServiceServer.bindTo(restClientBuilder).build();

        HttpServiceProxyFactory proxyFactory = HttpServiceProxyFactory.builderFor(
                RestClientAdapter.create(restClientBuilder.build())
        ).build();

        client = proxyFactory.createClient(UsageServiceClient.class);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void getSummary_shouldSendExpectedRequestAndMapResponse() {
        server.expect(request -> assertEquals(
                        "/internal/v1/usages/users/42/summary",
                        request.getURI().getPath()
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(queryParam("from", "2026-10-01T00%3A00%3A00Z"))
                .andExpect(queryParam("to", "2026-10-02T00%3A00%3A00Z"))
                .andRespond(withSuccess(
                        """
                        {
                          "success": true,
                          "data": {
                            "totalEnergyWh": 12500.50,
                            "averagePowerW": 520.25,
                            "maxPowerW": 1800.00,
                            "from": "2026-10-01T00:00:00Z",
                            "to": "2026-10-02T00:00:00Z"
                          },
                          "message": "Operation Successful"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        SuccessResponse<UsageSummaryResponse> response = client.getSummary(
                USER_ID,
                FROM,
                TO,
                INTERNAL_TOKEN
        );

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals("Operation Successful", response.message());

        UsageSummaryResponse summary = response.data();
        assertNotNull(summary);
        assertEquals(new BigDecimal("12500.50"), summary.totalEnergyWh());
        assertEquals(new BigDecimal("520.25"), summary.averagePowerW());
        assertEquals(new BigDecimal("1800.00"), summary.maxPowerW());
        assertEquals(FROM, summary.from());
        assertEquals(TO, summary.to());
    }
}
