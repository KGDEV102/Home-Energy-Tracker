package com.homeenergytracker.usageservice.integration.influx;

import com.homeenergytracker.usageservice.event.EnergyUsageEvent;
import com.homeenergytracker.usageservice.event.ReadingSource;
import com.homeenergytracker.usageservice.repository.InfluxEnergyUsageRepository;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class InfluxEnergyUsageRepositoryIntegrationTest {

    private static final String ORG = "home-energy-tracker-test";
    private static final String BUCKET = "energy-usage-test";
    private static final String TOKEN = "test-admin-token";

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
    private InfluxEnergyUsageRepository repository;

    @BeforeEach
    void setUp() {
        String url = "http://" + INFLUX_DB.getHost() + ":" + INFLUX_DB.getMappedPort(8086);

        influxDBClient = InfluxDBClientFactory.create(
                url,
                TOKEN.toCharArray(),
                ORG,
                BUCKET
        );
        repository = new InfluxEnergyUsageRepository(influxDBClient, BUCKET, ORG);
    }

    @AfterEach
    void tearDown() {
        influxDBClient.close();
    }

    @Test
    void shouldWriteAllEventDataToInfluxDB() {
        EnergyUsageEvent event = validEvent(UUID.randomUUID());

        repository.save(event);

        List<FluxRecord> records = queryRecords(event.deviceUid(), null);
        Map<String, FluxRecord> recordsByField = records.stream()
                .collect(Collectors.toMap(FluxRecord::getField, Function.identity()));

        assertThat(recordsByField).containsKeys(
                "event_id",
                "schema_version",
                "average_power_w",
                "interval_energy_wh",
                "received_at"
        );

        assertThat(recordsByField.get("event_id").getValue())
                .isEqualTo(event.eventId().toString());
        assertThat(recordsByField.get("schema_version").getValue())
                .isEqualTo((long) event.schemaVersion());
        assertThat(((Number) recordsByField.get("average_power_w").getValue()).doubleValue())
                .isEqualTo(event.averagePowerW().doubleValue());
        assertThat(((Number) recordsByField.get("interval_energy_wh").getValue()).doubleValue())
                .isEqualTo(event.intervalEnergyWh().doubleValue());
        assertThat(recordsByField.get("received_at").getValue())
                .isEqualTo(event.receivedAt().toString());

        FluxRecord eventIdRecord = recordsByField.get("event_id");
        assertThat(eventIdRecord.getTime()).isEqualTo(event.measuredAt());
        assertThat(eventIdRecord.getValueByKey("device_uid"))
                .isEqualTo(event.deviceUid().toString());
        assertThat(eventIdRecord.getValueByKey("user_id"))
                .isEqualTo(event.userId().toString());
        assertThat(eventIdRecord.getValueByKey("device_type"))
                .isEqualTo(event.deviceType());
        assertThat(eventIdRecord.getValueByKey("source"))
                .isEqualTo(event.source().name());
    }

    @Test
    void shouldNotCreateDuplicatePointWhenSameEventIsSavedTwice() {
        EnergyUsageEvent event = validEvent(UUID.randomUUID());

        repository.save(event);
        repository.save(event);

        List<FluxRecord> eventIdRecords = queryRecords(event.deviceUid(), "event_id");

        assertThat(eventIdRecords)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.getValue()).isEqualTo(event.eventId().toString());
                    assertThat(record.getTime()).isEqualTo(event.measuredAt());
                });
    }

    @Test
    void shouldStoreTwoMeasurementsWithDifferentTimestamps() {
        UUID deviceUid = UUID.randomUUID();
        Instant firstMeasuredAt = Instant.parse("2026-09-26T03:00:00Z");
        EnergyUsageEvent firstEvent = eventAt(deviceUid, firstMeasuredAt);
        EnergyUsageEvent secondEvent = eventAt(deviceUid, firstMeasuredAt.plusSeconds(5));

        repository.save(firstEvent);
        repository.save(secondEvent);

        List<FluxRecord> eventIdRecords = queryRecords(deviceUid, "event_id");

        assertThat(eventIdRecords)
                .hasSize(2)
                .extracting(FluxRecord::getValue)
                .containsExactlyInAnyOrder(
                        firstEvent.eventId().toString(),
                        secondEvent.eventId().toString()
                );
        assertThat(eventIdRecords)
                .extracting(FluxRecord::getTime)
                .containsExactlyInAnyOrder(
                        firstEvent.measuredAt(),
                        secondEvent.measuredAt()
                );
    }

    @Test
    void shouldKeepMeasurementsSeparatedByDeviceUid() {
        UUID firstDeviceUid = UUID.randomUUID();
        UUID secondDeviceUid = UUID.randomUUID();
        EnergyUsageEvent firstDeviceEvent = validEvent(firstDeviceUid);
        EnergyUsageEvent secondDeviceEvent = validEvent(secondDeviceUid);

        repository.save(firstDeviceEvent);
        repository.save(secondDeviceEvent);

        List<FluxRecord> firstDeviceRecords = queryRecords(firstDeviceUid, "event_id");
        List<FluxRecord> secondDeviceRecords = queryRecords(secondDeviceUid, "event_id");

        assertThat(firstDeviceRecords)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.getValue()).isEqualTo(firstDeviceEvent.eventId().toString());
                    assertThat(record.getValueByKey("device_uid"))
                            .isEqualTo(firstDeviceUid.toString());
                });
        assertThat(secondDeviceRecords)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.getValue()).isEqualTo(secondDeviceEvent.eventId().toString());
                    assertThat(record.getValueByKey("device_uid"))
                            .isEqualTo(secondDeviceUid.toString());
                });
    }

    private List<FluxRecord> queryRecords(UUID deviceUid, String field) {
        String fieldFilter = field == null
                ? ""
                : "  |> filter(fn: (r) => r._field == \"" + field + "\")\n";

        String query = """
                from(bucket: "%s")
                  |> range(start: 0)
                  |> filter(fn: (r) => r._measurement == "energy_usage")
                  |> filter(fn: (r) => r.device_uid == "%s")
                %s""".formatted(BUCKET, deviceUid, fieldFilter);

        return influxDBClient.getQueryApi()
                .query(query, ORG)
                .stream()
                .map(FluxTable::getRecords)
                .flatMap(List::stream)
                .toList();
    }

    private static EnergyUsageEvent validEvent(UUID deviceUid) {
        return eventAt(deviceUid, Instant.parse("2026-09-26T03:00:00Z"));
    }

    private static EnergyUsageEvent eventAt(UUID deviceUid, Instant measuredAt) {
        return new EnergyUsageEvent(
                UUID.randomUUID(),
                1,
                deviceUid,
                1L,
                "AIR_CONDITIONER",
                new BigDecimal("1200.50"),
                new BigDecimal("1.67"),
                measuredAt,
                measuredAt.plusSeconds(1),
                ReadingSource.SIMULATOR
        );
    }
}
