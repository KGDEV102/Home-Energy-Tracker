package com.homeenergytracker.usageservice.integration.api;

import com.homeenergytracker.common.event.EnergyUsageEvent;
import com.homeenergytracker.common.event.ReadingSource;
import com.homeenergytracker.usageservice.repository.InfluxEnergyUsageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/test-issuer"
})
@AutoConfigureMockMvc
class UsageApiIntegrationTest {

    private static final String ORG = "home-energy-tracker-api-test";
    private static final String BUCKET = "energy-usage-api-test";
    private static final String TOKEN = "api-test-admin-token";
    private static final AtomicLong USER_ID_SEQUENCE =
            new AtomicLong(10_000L);

    @Container
    private static final GenericContainer<?> INFLUX_DB =
            new GenericContainer<>(
                    DockerImageName.parse("influxdb:2.7.12-alpine")
            )
                    .withExposedPorts(8086)
                    .withEnv("DOCKER_INFLUXDB_INIT_MODE", "setup")
                    .withEnv("DOCKER_INFLUXDB_INIT_USERNAME", "test-user")
                    .withEnv("DOCKER_INFLUXDB_INIT_PASSWORD", "test-password")
                    .withEnv("DOCKER_INFLUXDB_INIT_ORG", ORG)
                    .withEnv("DOCKER_INFLUXDB_INIT_BUCKET", BUCKET)
                    .withEnv("DOCKER_INFLUXDB_INIT_ADMIN_TOKEN", TOKEN)
                    .waitingFor(Wait.forHttp("/health").forStatusCode(200))
                    .withStartupTimeout(Duration.ofMinutes(2));

    @DynamicPropertySource
    static void configureInfluxDb(DynamicPropertyRegistry registry) {
        registry.add(
                "influxdb.url",
                () -> "http://" + INFLUX_DB.getHost()
                        + ":" + INFLUX_DB.getMappedPort(8086)
        );
        registry.add("influxdb.token", () -> TOKEN);
        registry.add("influxdb.org", () -> ORG);
        registry.add("influxdb.bucket", () -> BUCKET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InfluxEnergyUsageRepository writeRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldReturnUsageSummaryForAuthenticatedUser()
            throws Exception {
        long userId = nextUserId();
        UUID firstDevice = UUID.randomUUID();
        UUID secondDevice = UUID.randomUUID();
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-01T00:03:00Z");

        saveEvent(firstDevice, userId, "AIR_CONDITIONER",
                "100", "1", from.plusSeconds(10));
        saveEvent(secondDevice, userId, "REFRIGERATOR",
                "50", "0.5", from.plusSeconds(20));
        saveEvent(firstDevice, userId, "AIR_CONDITIONER",
                "300", "3", from.plusSeconds(70));
        saveEvent(secondDevice, userId, "REFRIGERATOR",
                "150", "1.5", from.plusSeconds(80));

        // Hai point này không được xuất hiện trong kết quả.
        saveEvent(firstDevice, userId + 1, "AIR_CONDITIONER",
                "9000", "90", from.plusSeconds(30));
        saveEvent(firstDevice, userId, "AIR_CONDITIONER",
                "8000", "80", from.minusSeconds(10));

        mockMvc.perform(get("/api/v1/usages/summary")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                authorizationFor(userId)
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalEnergyWh").value(6.0))
                .andExpect(jsonPath("$.data.averagePowerW").value(300.0))
                .andExpect(jsonPath("$.data.maxPowerW").value(450.0))
                .andExpect(jsonPath("$.data.from").value(from.toString()))
                .andExpect(jsonPath("$.data.to").value(to.toString()));
    }

    @Test
    void shouldReturnUsageGroupedByDevice()
            throws Exception {
        long userId = nextUserId();
        UUID firstDevice = UUID.fromString(
                "11111111-1111-1111-1111-111111111111"
        );
        UUID secondDevice = UUID.fromString(
                "22222222-2222-2222-2222-222222222222"
        );
        Instant from = Instant.parse("2026-10-02T00:00:00Z");
        Instant to = Instant.parse("2026-10-02T01:00:00Z");

        saveEvent(firstDevice, userId, "AIR_CONDITIONER",
                "100", "1", from.plusSeconds(10));
        saveEvent(firstDevice, userId, "AIR_CONDITIONER",
                "300", "2", from.plusSeconds(20));
        saveEvent(secondDevice, userId, "REFRIGERATOR",
                "50", "4", from.plusSeconds(30));
        saveEvent(secondDevice, userId + 1, "REFRIGERATOR",
                "999", "99", from.plusSeconds(40));

        mockMvc.perform(get("/api/v1/usages/devices")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                authorizationFor(userId)
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].deviceUid")
                        .value(firstDevice.toString()))
                .andExpect(jsonPath("$.data[0].deviceType")
                        .value("AIR_CONDITIONER"))
                .andExpect(jsonPath("$.data[0].totalEnergyWh").value(3.0))
                .andExpect(jsonPath("$.data[0].averagePowerW").value(200.0))
                .andExpect(jsonPath("$.data[0].maxPowerW").value(300.0))
                .andExpect(jsonPath("$.data[1].deviceUid")
                        .value(secondDevice.toString()))
                .andExpect(jsonPath("$.data[1].totalEnergyWh").value(4.0));
    }

    @Test
    void shouldReturnDeviceTimeSeriesGroupedByWindow()
            throws Exception {
        long userId = nextUserId();
        UUID deviceUid = UUID.randomUUID();
        UUID otherDeviceUid = UUID.randomUUID();
        Instant from = Instant.parse("2026-10-03T00:00:00Z");
        Instant to = Instant.parse("2026-10-03T02:00:00Z");

        saveEvent(deviceUid, userId, "AIR_CONDITIONER",
                "100", "1", from.plusSeconds(600));
        saveEvent(deviceUid, userId, "AIR_CONDITIONER",
                "300", "3", from.plusSeconds(1200));
        saveEvent(deviceUid, userId, "AIR_CONDITIONER",
                "500", "5", from.plusSeconds(4200));

        saveEvent(otherDeviceUid, userId, "REFRIGERATOR",
                "900", "9", from.plusSeconds(900));
        saveEvent(deviceUid, userId + 1, "AIR_CONDITIONER",
                "800", "8", from.plusSeconds(1000));

        mockMvc.perform(get(
                        "/api/v1/usages/devices/{deviceUid}/timeseries",
                        deviceUid
                )
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("window", "1h")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                authorizationFor(userId)
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].timestamp")
                        .value("2026-10-03T01:00:00Z"))
                .andExpect(jsonPath("$.data[0].energyWh").value(4.0))
                .andExpect(jsonPath("$.data[0].averagePowerW").value(200.0))
                .andExpect(jsonPath("$.data[1].timestamp")
                        .value("2026-10-03T02:00:00Z"))
                .andExpect(jsonPath("$.data[1].energyWh").value(5.0))
                .andExpect(jsonPath("$.data[1].averagePowerW").value(500.0));
    }

    @Test
    void shouldReturnEmptyResultsWhenUserHasNoUsageData()
            throws Exception {
        long userId = nextUserId();
        Instant from = Instant.parse("2026-10-04T00:00:00Z");
        Instant to = Instant.parse("2026-10-04T01:00:00Z");

        mockMvc.perform(get("/api/v1/usages/devices")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                authorizationFor(userId)
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void shouldReturnBadRequestWhenFromIsNotBeforeTo()
            throws Exception {
        long userId = nextUserId();
        Instant instant = Instant.parse("2026-10-05T00:00:00Z");

        mockMvc.perform(get("/api/v1/usages/summary")
                        .param("from", instant.toString())
                        .param("to", instant.toString())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                authorizationFor(userId)
                        ))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestForUnsupportedWindow()
            throws Exception {
        long userId = nextUserId();
        UUID deviceUid = UUID.randomUUID();
        Instant from = Instant.parse("2026-10-06T00:00:00Z");
        Instant to = Instant.parse("2026-10-06T01:00:00Z");

        mockMvc.perform(get(
                        "/api/v1/usages/devices/{deviceUid}/timeseries",
                        deviceUid
                )
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("window", "7x")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                authorizationFor(userId)
                        ))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnUnauthorizedWithoutJwt()
            throws Exception {
        Instant from = Instant.parse("2026-10-07T00:00:00Z");
        Instant to = Instant.parse("2026-10-07T01:00:00Z");

        mockMvc.perform(get("/api/v1/usages/summary")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isUnauthorized());
    }

    private long nextUserId() {
        return USER_ID_SEQUENCE.incrementAndGet();
    }

    private String authorizationFor(long userId) {
        String tokenValue = "usage-api-token-" + userId;
        Instant now = Instant.now();

        Jwt jwt = Jwt.withTokenValue(tokenValue)
                .header("alg", "none")
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();

        when(jwtDecoder.decode(tokenValue)).thenReturn(jwt);

        return "Bearer " + tokenValue;
    }

    private void saveEvent(
            UUID deviceUid,
            long userId,
            String deviceType,
            String averagePowerW,
            String intervalEnergyWh,
            Instant measuredAt
    ) {
        writeRepository.save(new EnergyUsageEvent(
                UUID.randomUUID(),
                1,
                deviceUid,
                userId,
                deviceType,
                new BigDecimal(averagePowerW),
                new BigDecimal(intervalEnergyWh),
                measuredAt,
                measuredAt.plusSeconds(1),
                ReadingSource.SIMULATOR
        ));
    }
}
