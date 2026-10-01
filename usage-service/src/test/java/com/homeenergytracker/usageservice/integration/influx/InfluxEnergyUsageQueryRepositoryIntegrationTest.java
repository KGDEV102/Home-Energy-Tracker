package com.homeenergytracker.usageservice.integration.influx;

import com.homeenergytracker.common.event.EnergyUsageEvent;
import com.homeenergytracker.common.event.ReadingSource;
import com.homeenergytracker.usageservice.dto.DeviceUsageResponse;
import com.homeenergytracker.usageservice.dto.UsageDataPointResponse;
import com.homeenergytracker.usageservice.dto.UsageSummaryResponse;
import com.homeenergytracker.usageservice.repository.Impl.InfluxEnergyUsageQueryRepository;
import com.homeenergytracker.usageservice.repository.InfluxEnergyUsageRepository;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class InfluxEnergyUsageQueryRepositoryIntegrationTest {

    private static final String ORG = "home-energy-tracker-query-test";
    private static final String BUCKET = "energy-usage-query-test";
    private static final String TOKEN = "query-test-admin-token";
    private static final AtomicLong USER_ID_SEQUENCE = new AtomicLong(1000L);

    @Container
    private static final GenericContainer<?> INFLUX_DB =
            new GenericContainer<>(DockerImageName.parse("influxdb:2.7.12-alpine"))
                    .withExposedPorts(8086)
                    .withEnv("DOCKER_INFLUXDB_INIT_MODE", "setup")
                    .withEnv("DOCKER_INFLUXDB_INIT_USERNAME", "test-user")
                    .withEnv("DOCKER_INFLUXDB_INIT_PASSWORD", "test-password")
                    .withEnv("DOCKER_INFLUXDB_INIT_ORG", ORG)
                    .withEnv("DOCKER_INFLUXDB_INIT_BUCKET", BUCKET)
                    .withEnv("DOCKER_INFLUXDB_INIT_ADMIN_TOKEN", TOKEN)
                    .waitingFor(Wait.forHttp("/health").forStatusCode(200))
                    .withStartupTimeout(Duration.ofMinutes(2));

    private InfluxDBClient influxDBClient;
    private InfluxEnergyUsageRepository writeRepository;
    private InfluxEnergyUsageQueryRepository queryRepository;

    @BeforeEach
    void setUp() {
        String url = "http://" + INFLUX_DB.getHost()
                + ":" + INFLUX_DB.getMappedPort(8086);

        influxDBClient = InfluxDBClientFactory.create(
                url,
                TOKEN.toCharArray(),
                ORG,
                BUCKET
        );
        writeRepository = new InfluxEnergyUsageRepository(
                influxDBClient,
                BUCKET,
                ORG
        );
        queryRepository = new InfluxEnergyUsageQueryRepository(
                influxDBClient,
                BUCKET,
                ORG
        );
    }

    @AfterEach
    void tearDown() {
        influxDBClient.close();
    }

    @Test
    void shouldCalculateSummaryAndIgnoreOtherUsersAndOutsideRange() {
        long userId = USER_ID_SEQUENCE.incrementAndGet();
        UUID firstDevice = UUID.randomUUID();
        UUID secondDevice = UUID.randomUUID();
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-01T00:03:00Z");

        saveEvent(firstDevice, userId, "AIR_CONDITIONER", "100", "1", from.plusSeconds(10));
        saveEvent(secondDevice, userId, "REFRIGERATOR", "50", "0.5", from.plusSeconds(20));
        saveEvent(firstDevice, userId, "AIR_CONDITIONER", "300", "3", from.plusSeconds(70));
        saveEvent(secondDevice, userId, "REFRIGERATOR", "150", "1.5", from.plusSeconds(80));

        saveEvent(firstDevice, userId + 1, "AIR_CONDITIONER", "9000", "90", from.plusSeconds(30));
        saveEvent(firstDevice, userId, "AIR_CONDITIONER", "8000", "80", from.minusSeconds(10));

        UsageSummaryResponse result =
                queryRepository.findSummary(userId, from, to);

        assertThat(result.totalEnergyWh()).isEqualByComparingTo("6.0");
        assertThat(result.averagePowerW()).isEqualByComparingTo("300.0");
        assertThat(result.maxPowerW()).isEqualByComparingTo("450.0");
        assertThat(result.from()).isEqualTo(from);
        assertThat(result.to()).isEqualTo(to);
    }

    @Test
    void shouldAggregateUsageSeparatelyForEachDevice() {
        long userId = USER_ID_SEQUENCE.incrementAndGet();
        UUID firstDevice = UUID.randomUUID();
        UUID secondDevice = UUID.randomUUID();
        Instant from = Instant.parse("2026-10-02T00:00:00Z");
        Instant to = Instant.parse("2026-10-02T01:00:00Z");

        saveEvent(firstDevice, userId, "AIR_CONDITIONER", "100", "1", from.plusSeconds(10));
        saveEvent(firstDevice, userId, "AIR_CONDITIONER", "300", "2", from.plusSeconds(20));
        saveEvent(secondDevice, userId, "REFRIGERATOR", "50", "4", from.plusSeconds(30));
        saveEvent(secondDevice, userId + 1, "REFRIGERATOR", "999", "99", from.plusSeconds(40));

        List<DeviceUsageResponse> result =
                queryRepository.findUsageByDevice(userId, from, to);

        Map<UUID, DeviceUsageResponse> byDevice = result.stream()
                .collect(Collectors.toMap(
                        DeviceUsageResponse::deviceUid,
                        Function.identity()
                ));

        assertThat(byDevice).containsOnlyKeys(firstDevice, secondDevice);
        assertThat(byDevice.get(firstDevice).deviceType())
                .isEqualTo("AIR_CONDITIONER");
        assertThat(byDevice.get(firstDevice).totalEnergyWh())
                .isEqualByComparingTo("3.0");
        assertThat(byDevice.get(firstDevice).averagePowerW())
                .isEqualByComparingTo("200.0");
        assertThat(byDevice.get(firstDevice).maxPowerW())
                .isEqualByComparingTo("300.0");

        assertThat(byDevice.get(secondDevice).deviceType())
                .isEqualTo("REFRIGERATOR");
        assertThat(byDevice.get(secondDevice).totalEnergyWh())
                .isEqualByComparingTo("4.0");
        assertThat(byDevice.get(secondDevice).averagePowerW())
                .isEqualByComparingTo("50.0");
        assertThat(byDevice.get(secondDevice).maxPowerW())
                .isEqualByComparingTo("50.0");
    }

    @Test
    void shouldAggregateDeviceTimeSeriesByWindowAndSortByTime() {
        long userId = USER_ID_SEQUENCE.incrementAndGet();
        UUID deviceUid = UUID.randomUUID();
        UUID otherDeviceUid = UUID.randomUUID();
        Instant from = Instant.parse("2026-10-03T00:00:00Z");
        Instant to = Instant.parse("2026-10-03T02:00:00Z");

        saveEvent(deviceUid, userId, "AIR_CONDITIONER", "100", "1", from.plusSeconds(600));
        saveEvent(deviceUid, userId, "AIR_CONDITIONER", "300", "3", from.plusSeconds(1200));
        saveEvent(deviceUid, userId, "AIR_CONDITIONER", "500", "5", from.plusSeconds(4200));

        saveEvent(otherDeviceUid, userId, "REFRIGERATOR", "900", "9", from.plusSeconds(900));
        saveEvent(deviceUid, userId + 1, "AIR_CONDITIONER", "800", "8", from.plusSeconds(1000));

        List<UsageDataPointResponse> result =
                queryRepository.findDeviceTimeSeries(
                        userId,
                        deviceUid,
                        from,
                        to,
                        "1h"
                );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).timestamp())
                .isEqualTo(Instant.parse("2026-10-03T01:00:00Z"));
        assertThat(result.get(0).energyWh())
                .isEqualByComparingTo("4.0");
        assertThat(result.get(0).averagePowerW())
                .isEqualByComparingTo("200.0");

        assertThat(result.get(1).timestamp())
                .isEqualTo(Instant.parse("2026-10-03T02:00:00Z"));
        assertThat(result.get(1).energyWh())
                .isEqualByComparingTo("5.0");
        assertThat(result.get(1).averagePowerW())
                .isEqualByComparingTo("500.0");
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
