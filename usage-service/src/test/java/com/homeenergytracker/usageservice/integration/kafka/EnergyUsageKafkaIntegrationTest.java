package com.homeenergytracker.usageservice.integration.kafka;

import com.homeenergytracker.common.event.EnergyUsageEvent;
import com.homeenergytracker.common.event.ReadingSource;
import com.homeenergytracker.usageservice.repository.EnergyUsageRepository;
import com.homeenergytracker.usageservice.validation.EnergyUsageEventValidator;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "usage.kafka.topic=energy-usage.v1-test",
        "usage.kafka.dlt-topic=energy-usage.v1-dlt-test",
        "usage.kafka.retry-attempts=2",
        "usage.kafka.retry-interval-ms=10",
        "influxdb.token=test-token",
        "internal.usage-service-token=test-internal-token"
})
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "energy-usage.v1-test",
                "energy-usage.v1-dlt-test"
        },
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class EnergyUsageKafkaIntegrationTest {

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final String TOPIC = "energy-usage.v1-test";
    private static final String DLT_TOPIC = "energy-usage.v1-dlt-test";

    /*
     * Dùng KafkaTemplate dành cho DLT để phát dữ liệu đầu vào trong test.
     * Template này đã hỗ trợ String key và EnergyUsageEvent value.
     */
    @Autowired
    @Qualifier("dltKafkaTemplate")
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    /*
     * Thay repository thật bằng mock để test chỉ tập trung vào Kafka:
     * serialize -> broker -> deserialize -> listener -> validator.
     */
    @MockitoBean
    private EnergyUsageRepository repository;

    @MockitoSpyBean
    private EnergyUsageEventValidator validator;

    @Test
    void shouldConsumeValidEventAndSaveIt() throws Exception {
        // Arrange
        EnergyUsageEvent event = validEvent();
        String kafkaKey = event.deviceUid().toString();

        // Act: gửi event vào Kafka broker nhúng và chờ broker xác nhận.
        kafkaTemplate.send(
                TOPIC,
                kafkaKey,
                event
        ).get(10, TimeUnit.SECONDS);

        /*
         * Listener chạy bất đồng bộ, nên Mockito chờ tối đa 10 giây.
         * Nếu consumer hoạt động đúng, nó sẽ gọi repository.save(event).
         */
        verify(repository, timeout(10_000))
                .save(event);
    }

    @Test
    void shouldSendInvalidEventDirectlyToDlt() throws Exception {
        // Arrange: dữ liệu sai nghiệp vụ nên retry không thể sửa được.
        EnergyUsageEvent event = eventWithPower(
                new BigDecimal("-0.01")
        );
        String kafkaKey = event.deviceUid().toString();

        // Act
        send(kafkaKey, event);

        ConsumerRecord<String, byte[]> dltRecord =
                awaitDltRecord(kafkaKey);

        // Assert: message vào DLT và repository không được gọi.
        assertThat(dltRecord.key()).isEqualTo(kafkaKey);
        assertThat(new String(
                dltRecord.value(),
                StandardCharsets.UTF_8
        )).contains(event.eventId().toString());

        verify(validator).validate(kafkaKey, event);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRetryAndSendToDltWhenRepositoryKeepsFailing()
            throws Exception {
        // Arrange
        EnergyUsageEvent event = validEvent();
        String kafkaKey = event.deviceUid().toString();

        doThrow(new RuntimeException("InfluxDB unavailable"))
                .when(repository)
                .save(event);

        // Act
        send(kafkaKey, event);

        ConsumerRecord<String, byte[]> dltRecord =
                awaitDltRecord(kafkaKey);

        // 1 lần đầu + 2 lần retry = tổng cộng 3 lần gọi repository.
        verify(repository, timeout(10_000).times(3))
                .save(event);

        assertThat(dltRecord.key()).isEqualTo(kafkaKey);
        assertThat(dltRecord.value()).isNotEmpty();
    }

    @Test
    void shouldSucceedWhenRepositoryRecoversDuringRetry()
            throws Exception {
        // Arrange
        EnergyUsageEvent event = validEvent();
        String kafkaKey = event.deviceUid().toString();

        /*
         * Lần đầu ném lỗi, lần thứ hai không làm gì và được xem là thành công.
         */
        doThrow(new RuntimeException("Temporary InfluxDB error"))
                .doNothing()
                .when(repository)
                .save(event);

        // Act
        send(kafkaKey, event);

        // Assert: consumer retry đúng một lần rồi dừng.
        verify(repository, timeout(10_000).times(2))
                .save(event);

        assertThat(findDltRecord(kafkaKey, Duration.ofSeconds(1)))
                .isEmpty();
    }

    @Test
    void shouldSendMalformedJsonToDlt() throws Exception {
        // Arrange
        String kafkaKey = UUID.randomUUID().toString();
        byte[] malformedJson = "{\"eventId\":".getBytes(
                StandardCharsets.UTF_8
        );

        // Act: gửi raw byte[] để JSON deserializer nhận dữ liệu bị hỏng.
        send(kafkaKey, malformedJson);

        ConsumerRecord<String, byte[]> dltRecord =
                awaitDltRecord(kafkaKey);

        // Assert: DLT giữ lại đúng dữ liệu gốc để điều tra sau này.
        assertThat(dltRecord.key()).isEqualTo(kafkaKey);
        assertThat(dltRecord.value()).isEqualTo(malformedJson);

        verify(repository, never()).save(any());
    }

    private void send(String kafkaKey, Object value)
            throws Exception {
        kafkaTemplate.send(
                TOPIC,
                kafkaKey,
                value
        ).get(10, TimeUnit.SECONDS);
    }

    private ConsumerRecord<String, byte[]> awaitDltRecord(
            String expectedKey
    ) {
        return findDltRecord(
                expectedKey,
                Duration.ofSeconds(10)
        ).orElseThrow(
                () -> new AssertionError(
                        "No DLT record found for key: " + expectedKey
                )
        );
    }

    private Optional<ConsumerRecord<String, byte[]>> findDltRecord(
            String expectedKey,
            Duration timeout
    ) {
        Map<String, Object> consumerProperties =
                KafkaTestUtils.consumerProps(
                        embeddedKafkaBroker,
                        "dlt-verifier-" + UUID.randomUUID(),
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

            /*
             * Gán trực tiếp consumer test vào DLT và đọc từ đầu.
             * Mỗi event dùng key riêng nên có thể bỏ qua record của test khác.
             */
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

    private EnergyUsageEvent validEvent() {
        return eventWithPower(new BigDecimal("1200.50"));
    }

    private EnergyUsageEvent eventWithPower(
            BigDecimal averagePowerW
    ) {
        Instant measuredAt =
                Instant.parse("2026-09-26T03:00:00Z");

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
