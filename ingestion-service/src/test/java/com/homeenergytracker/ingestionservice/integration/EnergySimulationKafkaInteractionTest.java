package com.homeenergytracker.ingestionservice.integration;

import com.homeenergytracker.ingestionservice.client.DeviceServiceClient;
import com.homeenergytracker.ingestionservice.dto.DeviceSimulationTargetResponse;
import com.homeenergytracker.common.event.EnergyUsageEvent;
import com.homeenergytracker.common.event.ReadingSource;
import com.homeenergytracker.ingestionservice.simulation.EnergySimulationJob;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        /*
         * Không cho scheduler tự chạy trong lúc test.
         * Test sẽ chủ động gọi refreshDevices() và simulate().
         */
        "simulation.refresh-initial-delay-ms=3600000",
        "simulation.job-initial-delay-ms=3600000",

        "simulation.interval-ms=5000",
        "ingestion.kafka.topic=energy-usage.v1"
})
@EmbeddedKafka(
        partitions = 1,
        topics = "energy-usage.v1",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
public class EnergySimulationKafkaInteractionTest {
    private static final String TOPIC = "energy-usage.v1";
    private static final long INTERVAL_MILLIS = 5_000L;
    private static final UUID DEVICE_UID =
            UUID.fromString("89fe1560-9507-4a5b-a1dc-37de87e9b50b");

    /*
     * Đây là bean thật của ứng dụng.
     * Bên trong nó sử dụng KafkaTemplate thật.
     */
    @Autowired
    private EnergySimulationJob job;

    /*
     * Mock device-service vì test này chỉ tập trung vào Kafka.
     */
    @MockitoBean
    private DeviceServiceClient deviceServiceClient;

    /*
     * Kafka broker được tạo riêng trong bộ nhớ cho integration test.
     */
    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;
    private Consumer<String, EnergyUsageEvent> consumer;

    @BeforeEach
    void setUpConsumer() {
        Map<String, Object> consumerProperties =
                KafkaTestUtils.consumerProps(
                        embeddedKafka,
                        "ingestion-integration-test-" + UUID.randomUUID(),
                        false
                );

        consumerProperties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );

        JacksonJsonDeserializer<EnergyUsageEvent> valueDeserializer =
                new JacksonJsonDeserializer<>(
                        EnergyUsageEvent.class,
                        false
                );

        valueDeserializer.addTrustedPackages(
                "com.homeenergytracker.common.event"
        );

        consumer = new DefaultKafkaConsumerFactory<>(
                consumerProperties,
                new StringDeserializer(),
                valueDeserializer
        ).createConsumer();

        embeddedKafka.consumeFromAnEmbeddedTopic(
                consumer,
                TOPIC
        );
    }
    @AfterEach
    void closeConsumer() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void simulate_shouldPublishEventToKafka() {
        // Arrange
        BigDecimal ratedPowerW = new BigDecimal("1000");

        DeviceSimulationTargetResponse device =
                new DeviceSimulationTargetResponse(
                        DEVICE_UID,
                        10L,
                        "AIR_CONDITIONER",
                        ratedPowerW
                );

        when(deviceServiceClient.getSimulationTargets())
                .thenReturn(com.homeenergytracker.common.response.SuccessResponse.of(List.of(device)));

        // Act
        job.refreshDevices();
        job.simulate();

        // Đọc message thật từ Embedded Kafka
        ConsumerRecord<String, EnergyUsageEvent> record =
                KafkaTestUtils.getSingleRecord(
                        consumer,
                        TOPIC,
                        Duration.ofSeconds(10)
                );

        // Assert Kafka record
        assertNotNull(record);
        assertEquals(DEVICE_UID.toString(), record.key());

        EnergyUsageEvent event = record.value();

        assertNotNull(event);

        // Kiểm tra metadata
        assertNotNull(event.eventId());
        assertEquals(1, event.schemaVersion());
        assertEquals(ReadingSource.SIMULATOR, event.source());

        // Kiểm tra thông tin thiết bị
        assertEquals(DEVICE_UID, event.deviceUid());
        assertEquals(10L, event.userId());
        assertEquals("AIR_CONDITIONER", event.deviceType());

        // Kiểm tra thời gian
        assertNotNull(event.measuredAt());
        assertNotNull(event.receivedAt());

        assertFalse(
                event.receivedAt().isBefore(event.measuredAt())
        );

        // Kiểm tra công suất random
        assertNotNull(event.averagePowerW());
        assertTrue(event.averagePowerW().signum() >= 0);
        assertTrue(
                event.averagePowerW().compareTo(ratedPowerW) <= 0
        );

        // Kiểm tra công thức tính năng lượng
        BigDecimal expectedEnergyWh = event.averagePowerW()
                .multiply(BigDecimal.valueOf(INTERVAL_MILLIS))
                .divide(
                        BigDecimal.valueOf(3_600_000),
                        6,
                        RoundingMode.HALF_UP
                );

        assertEquals(
                expectedEnergyWh,
                event.intervalEnergyWh()
        );
    }

}
