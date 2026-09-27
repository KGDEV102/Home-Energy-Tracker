package com.homeenergytracker.usageservice.integration.flow;

import com.homeenergytracker.usageservice.event.EnergyUsageEvent;
import com.homeenergytracker.usageservice.event.ReadingSource;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "usage.kafka.topic=energy-usage.v1-flow-test",
        "usage.kafka.dlt-topic=energy-usage.v1-flow-test-dlt",
        "usage.kafka.retry-attempts=2",
        "usage.kafka.retry-interval-ms=10"
})
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "energy-usage.v1-flow-test",
                "energy-usage.v1-flow-test-dlt"
        },
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class EnergyUsageFlowIntegrationTest {

    private static final String TOPIC = "energy-usage.v1-flow-test";
    private static final String DLT_TOPIC = "energy-usage.v1-flow-test-dlt";
    private static final String ORG = "home-energy-tracker-flow-test";
    private static final String BUCKET = "energy-usage-flow-test";
    private static final String TOKEN = "flow-test-admin-token";

    @Container
    private static final GenericContainer<?> INFLUX_DB =
            new GenericContainer<>(DockerImageName.parse("influxdb:2.7.12-alpine"))
                    .withExposedPorts(8086)
                    .withEnv("DOCKER_INFLUXDB_INIT_MODE", "setup")
                    .withEnv("DOCKER_INFLUXDB_INIT_USERNAME", "flow-test-user")
                    .withEnv("DOCKER_INFLUXDB_INIT_PASSWORD", "flow-test-password")
                    .withEnv("DOCKER_INFLUXDB_INIT_ORG", ORG)
                    .withEnv("DOCKER_INFLUXDB_INIT_BUCKET", BUCKET)
                    .withEnv("DOCKER_INFLUXDB_INIT_ADMIN_TOKEN", TOKEN)
                    .waitingFor(Wait.forHttp("/health").forStatusCode(200))
                    .withStartupTimeout(Duration.ofMinutes(2));

    @DynamicPropertySource
    static void configureInfluxDB(DynamicPropertyRegistry registry) {
        registry.add(
                "influxdb.url",
                () -> "http://" + INFLUX_DB.getHost() + ":" + INFLUX_DB.getMappedPort(8086)
        );
        registry.add("influxdb.token", () -> TOKEN);
        registry.add("influxdb.org", () -> ORG);
        registry.add("influxdb.bucket", () -> BUCKET);
    }

    @Autowired
    @Qualifier("dltKafkaTemplate")
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private InfluxDBClient influxDBClient;

    @Test
    void shouldConsumeValidEventAndStoreItInInfluxDB() throws Exception {
        EnergyUsageEvent event = eventWithPower(new BigDecimal("1200.50"));
        String kafkaKey = event.deviceUid().toString();

        send(kafkaKey, event);

        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> {
                    List<FluxRecord> records = queryEventIdRecords(event.deviceUid());

                    assertThat(records)
                            .singleElement()
                            .satisfies(record -> {
                                assertThat(record.getValue())
                                        .isEqualTo(event.eventId().toString());
                                assertThat(record.getTime())
                                        .isEqualTo(event.measuredAt());
                                assertThat(record.getValueByKey("device_uid"))
                                        .isEqualTo(event.deviceUid().toString());
                            });
                });
    }

    @Test
    void shouldSendInvalidEventToDltWithoutStoringItInInfluxDB() throws Exception {
        EnergyUsageEvent event = eventWithPower(new BigDecimal("-0.01"));
        String kafkaKey = event.deviceUid().toString();

        send(kafkaKey, event);

        ConsumerRecord<String, byte[]> dltRecord = awaitDltRecord(kafkaKey);

        assertThat(dltRecord.key()).isEqualTo(kafkaKey);
        assertThat(new String(dltRecord.value(), StandardCharsets.UTF_8))
                .contains(event.eventId().toString());
        assertThat(queryEventIdRecords(event.deviceUid())).isEmpty();
    }

    private void send(String kafkaKey, EnergyUsageEvent event) throws Exception {
        kafkaTemplate.send(TOPIC, kafkaKey, event)
                .get(10, TimeUnit.SECONDS);
    }

    private List<FluxRecord> queryEventIdRecords(UUID deviceUid) {
        String query = """
                from(bucket: "%s")
                  |> range(start: 0)
                  |> filter(fn: (r) => r._measurement == "energy_usage")
                  |> filter(fn: (r) => r.device_uid == "%s")
                  |> filter(fn: (r) => r._field == "event_id")
                """.formatted(BUCKET, deviceUid);

        return influxDBClient.getQueryApi()
                .query(query, ORG)
                .stream()
                .map(FluxTable::getRecords)
                .flatMap(List::stream)
                .toList();
    }

    private ConsumerRecord<String, byte[]> awaitDltRecord(String expectedKey) {
        return findDltRecord(expectedKey, Duration.ofSeconds(10))
                .orElseThrow(() -> new AssertionError(
                        "No DLT record found for key: " + expectedKey
                ));
    }

    private Optional<ConsumerRecord<String, byte[]>> findDltRecord(
            String expectedKey,
            Duration timeout
    ) {
        Map<String, Object> consumerProperties = KafkaTestUtils.consumerProps(
                embeddedKafkaBroker,
                "flow-dlt-verifier-" + UUID.randomUUID(),
                false
        );
        consumerProperties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );

        try (Consumer<String, byte[]> consumer =
                     new DefaultKafkaConsumerFactory<>(
                             consumerProperties,
                             new StringDeserializer(),
                             new ByteArrayDeserializer()
                     ).createConsumer()) {

            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(
                    consumer,
                    false,
                    DLT_TOPIC
            );

            long deadline = System.nanoTime() + timeout.toNanos();

            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, byte[]> record :
                        KafkaTestUtils.getRecords(
                                consumer,
                                Duration.ofMillis(250)
                        )) {
                    if (expectedKey.equals(record.key())) {
                        return Optional.of(record);
                    }
                }
            }

            return Optional.empty();
        }
    }

    private EnergyUsageEvent eventWithPower(BigDecimal averagePowerW) {
        Instant measuredAt = Instant.now().minusSeconds(1);

        return new EnergyUsageEvent(
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                1L,
                "AIR_CONDITIONER",
                averagePowerW,
                new BigDecimal("1.67"),
                measuredAt,
                measuredAt.plusSeconds(1),
                ReadingSource.SIMULATOR
        );
    }
}
