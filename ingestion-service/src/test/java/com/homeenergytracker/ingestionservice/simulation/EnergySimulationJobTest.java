package com.homeenergytracker.ingestionservice.simulation;

import com.homeenergytracker.ingestionservice.client.DeviceSimulationTargetGateway;
import com.homeenergytracker.ingestionservice.dto.DeviceSimulationTargetResponse;
import com.homeenergytracker.common.event.EnergyUsageEvent;
import com.homeenergytracker.common.event.ReadingSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EnergySimulationJobTest {
    private static final String TOPIC = "energy-usage.v1";
    private static final long INTERVAL_MILLIS = 5_000L;

    private static final UUID DEVICE_UID =
            UUID.fromString("89fe1560-9507-4a5b-a1dc-37de87e9b50b");

    @Mock
    private DeviceSimulationTargetGateway deviceTargetGateway;

    @Mock
    private KafkaTemplate<String, EnergyUsageEvent> kafkaTemplate;

    private EnergySimulationJob job;

    @BeforeEach
    void setUp() {
        job = new EnergySimulationJob(
                deviceTargetGateway,
                kafkaTemplate,
                TOPIC,
                INTERVAL_MILLIS
        );
    }
    private void mockSuccessfulKafkaSend() {
        CompletableFuture<SendResult<String, EnergyUsageEvent>>
                completedFuture = CompletableFuture.completedFuture(null);

        when(kafkaTemplate.send(
                anyString(),
                anyString(),
                any(EnergyUsageEvent.class)
        )).thenReturn(completedFuture);
    }

    private DeviceSimulationTargetResponse createDevice(
            UUID deviceUid,
            BigDecimal ratedPowerW
    ) {
        return new DeviceSimulationTargetResponse(
                deviceUid,
                10L,
                "AIR_CONDITIONER",
                ratedPowerW
        );
    }

    @Test
    void refreshDevices_shouldStoreDevices_whenResponseIsSuccessful() {
        // Arrange
        DeviceSimulationTargetResponse device =
                createDevice(DEVICE_UID, new BigDecimal("1000"));

        when(deviceTargetGateway.loadTargets())
                .thenReturn(Optional.of(List.of(device)));
        mockSuccessfulKafkaSend();

        // Act
        job.refreshDevices();
        job.simulate();
        // Assert
        verify(kafkaTemplate).send(
                eq(TOPIC),
                eq(DEVICE_UID.toString()),
                any(EnergyUsageEvent.class)
        );

    }
    @Test
    void refreshDevices_shouldKeepExistingDevices_whenResponseIsInvalid() {
        // Arrange: lần refresh đầu tiên thành công
        DeviceSimulationTargetResponse device =
                createDevice(DEVICE_UID, new BigDecimal("1000"));
        when(deviceTargetGateway.loadTargets())
                .thenReturn(Optional.of(List.of(device)))
                .thenReturn(Optional.empty());
        mockSuccessfulKafkaSend();
        // Act: lưu danh sách hợp lệ
        job.refreshDevices();

        // Refresh lần hai lỗi, danh sách cũ phải được giữ lại
        job.refreshDevices();

        job.simulate();

        // Assert
        verify(kafkaTemplate).send(
                eq(TOPIC),
                eq(DEVICE_UID.toString()),
                any(EnergyUsageEvent.class)
        );
    }

    @Test
    void refreshDevices_shouldKeepExistingDevices_whenClientThrowsException(){
        // Arrange
        DeviceSimulationTargetResponse device =
                createDevice(DEVICE_UID, new BigDecimal("1000"));

        when(deviceTargetGateway.loadTargets())
                .thenReturn(Optional.of(List.of(device)))
                .thenReturn(Optional.empty());
        mockSuccessfulKafkaSend();
        // Act
        job.refreshDevices();
        assertDoesNotThrow(job::refreshDevices);
        assertDoesNotThrow(job::simulate);
        // Assert: vẫn dùng danh sách lấy được từ lần refresh đầu
        verify(kafkaTemplate).send(
                eq(TOPIC),
                eq(DEVICE_UID.toString()),
                any(EnergyUsageEvent.class)
        );


    }
    @Test
    void simulate_shouldNotPublish_whenDeviceListIsEmpty() {
        // Act
        job.simulate();

        // Assert
        verifyNoInteractions(kafkaTemplate);
    }
    @Test
    void simulate_shouldSkipDevices_whenRatedPowerIsInvalid() {
        // Arrange
        List<DeviceSimulationTargetResponse> devices = List.of(
                createDevice(UUID.randomUUID(), null),
                createDevice(UUID.randomUUID(), BigDecimal.ZERO),
                createDevice(UUID.randomUUID(), new BigDecimal("-100"))
        );

        when(deviceTargetGateway.loadTargets())
                .thenReturn(Optional.of(devices));

        // Act
        job.refreshDevices();
        job.simulate();

        // Assert
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void simulate_shouldNotPublish_whenRequiredEventDataIsInvalid() {
        List<DeviceSimulationTargetResponse> devices = List.of(
                new DeviceSimulationTargetResponse(
                        null,
                        10L,
                        "AIR_CONDITIONER",
                        new BigDecimal("1000")
                ),
                new DeviceSimulationTargetResponse(
                        UUID.randomUUID(),
                        null,
                        "AIR_CONDITIONER",
                        new BigDecimal("1000")
                ),
                new DeviceSimulationTargetResponse(
                        UUID.randomUUID(),
                        10L,
                        "   ",
                        new BigDecimal("1000")
                )
        );

        when(deviceTargetGateway.loadTargets())
                .thenReturn(Optional.of(devices));

        job.refreshDevices();
        assertDoesNotThrow(job::simulate);

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void simulate_shouldCreateAndPublishValidEvent() {
        // Arrange
        BigDecimal ratedPowerW = new BigDecimal("1000");

        DeviceSimulationTargetResponse device =
                createDevice(DEVICE_UID, ratedPowerW);

        when(deviceTargetGateway.loadTargets())
                .thenReturn(Optional.of(List.of(device)));

        mockSuccessfulKafkaSend();

        ArgumentCaptor<EnergyUsageEvent> eventCaptor =
                ArgumentCaptor.forClass(EnergyUsageEvent.class);

        // Act
        job.refreshDevices();
        job.simulate();

        // Assert: kiểm tra topic, key và lấy event được gửi
        verify(kafkaTemplate).send(
                eq(TOPIC),
                eq(DEVICE_UID.toString()),
                eventCaptor.capture()
        );

        EnergyUsageEvent event = eventCaptor.getValue();

        assertNotNull(event.eventId());
        assertEquals(1, event.schemaVersion());
        assertEquals(DEVICE_UID, event.deviceUid());
        assertEquals(10L, event.userId());
        assertEquals("AIR_CONDITIONER", event.deviceType());
        assertEquals(ReadingSource.SIMULATOR, event.source());

        assertNotNull(event.measuredAt());
        assertNotNull(event.receivedAt());

        // receivedAt phải bằng hoặc sau measuredAt
        assertFalse(event.receivedAt().isBefore(event.measuredAt()));

        // Công suất random phải nằm từ 0 đến công suất định mức
        assertTrue(event.averagePowerW().signum() >= 0);
        assertTrue(event.averagePowerW().compareTo(ratedPowerW) <= 0);

        // Kiểm tra công thức tính điện năng trong khoảng 5 giây
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
    @Test
    void simulate_shouldPublishOneEventForEachValidDevice() {
        // Arrange
        List<DeviceSimulationTargetResponse> devices = List.of(
                createDevice(UUID.randomUUID(), new BigDecimal("1000")),
                createDevice(UUID.randomUUID(), new BigDecimal("500")),

                // Device này không hợp lệ nên phải bị bỏ qua
                createDevice(UUID.randomUUID(), BigDecimal.ZERO)
        );

        when(deviceTargetGateway.loadTargets())
                .thenReturn(Optional.of(devices));

        mockSuccessfulKafkaSend();

        // Act
        job.refreshDevices();
        job.simulate();

        // Assert: chỉ có hai device hợp lệ
        verify(kafkaTemplate, times(2)).send(
                eq(TOPIC),
                anyString(),
                any(EnergyUsageEvent.class)
        );
    }

    @Test
    void simulate_shouldNotThrow_whenKafkaPublishFailsAsynchronously() {
        // Arrange
        DeviceSimulationTargetResponse device =
                createDevice(DEVICE_UID, new BigDecimal("1000"));

        when(deviceTargetGateway.loadTargets())
                .thenReturn(Optional.of(List.of(device)));

        CompletableFuture<SendResult<String, EnergyUsageEvent>>
                failedFuture = new CompletableFuture<>();

        failedFuture.completeExceptionally(
                new RuntimeException("Kafka unavailable")
        );

        when(kafkaTemplate.send(
                anyString(),
                anyString(),
                any(EnergyUsageEvent.class)
        )).thenReturn(failedFuture);

        // Act
        job.refreshDevices();

        // Assert: callback xử lý lỗi, không làm scheduled method văng lỗi
        assertDoesNotThrow(job::simulate);

        verify(kafkaTemplate).send(
                eq(TOPIC),
                eq(DEVICE_UID.toString()),
                any(EnergyUsageEvent.class)
        );
    }

    @Test
    void simulate_shouldNotThrow_whenKafkaSendFailsSynchronously() {
        DeviceSimulationTargetResponse device =
                createDevice(DEVICE_UID, new BigDecimal("1000"));

        when(deviceTargetGateway.loadTargets())
                .thenReturn(Optional.of(List.of(device)));

        when(kafkaTemplate.send(
                anyString(),
                anyString(),
                any(EnergyUsageEvent.class)
        )).thenThrow(new RuntimeException("Kafka producer buffer unavailable"));

        job.refreshDevices();

        assertDoesNotThrow(job::simulate);

        verify(kafkaTemplate).send(
                eq(TOPIC),
                eq(DEVICE_UID.toString()),
                any(EnergyUsageEvent.class)
        );
    }



}
