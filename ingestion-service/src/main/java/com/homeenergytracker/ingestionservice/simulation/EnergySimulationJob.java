package com.homeenergytracker.ingestionservice.simulation;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.ingestionservice.client.DeviceServiceClient;
import com.homeenergytracker.ingestionservice.dto.DeviceSimulationTargetResponse;
import com.homeenergytracker.common.event.EnergyUsageEvent;
import com.homeenergytracker.common.event.ReadingSource;
import com.homeenergytracker.ingestionservice.model.TelemetryReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class EnergySimulationJob {
    private static final Logger log =
            LoggerFactory.getLogger(EnergySimulationJob.class);
    private static final BigDecimal MILLISECONDS_PER_HOUR =
            BigDecimal.valueOf(3_600_000);
    private final DeviceServiceClient deviceServiceClient;
    private final KafkaTemplate<String, EnergyUsageEvent> kafkaTemplate;
    private final String topic;
    private final long intervalMillis;

    /*
     * Danh sách thiết bị lưu tạm trong RAM.
     *
     * volatile giúp job simulate luôn đọc được danh sách mới nhất
     * sau khi refreshDevices cập nhật.
     */
    private volatile List<DeviceSimulationTargetResponse> devices =
            List.of();
    public EnergySimulationJob(
            DeviceServiceClient deviceServiceClient,
            KafkaTemplate<String,EnergyUsageEvent> kafkaTemplate,
            @Value("${ingestion.kafka.topic}") String topic,
            @Value("${simulation.interval-ms:5000}") long intervalMillis

    ){
        this.deviceServiceClient = deviceServiceClient;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.intervalMillis = intervalMillis;

    }
    @Scheduled(
            initialDelayString = "${simulation.refresh-initial-delay-ms:0}",
            fixedDelayString = "${simulation.refresh-devices-ms:60000}"
    )
    public void refreshDevices(){
        try{
            SuccessResponse<List<DeviceSimulationTargetResponse>> response = deviceServiceClient.getSimulationTargets();
            if(response == null
                    || !response.success()
                    || response.data() == null
            ){
                log.warn("Invalid response from device-service");
                return;
            }
            devices = List.copyOf(response.data());

            log.info(
                    "Loaded {} devices for simulation",
                    devices.size()
            );


        }catch (Exception ex){
            /*
             * Nếu device-service đang lỗi, giữ nguyên danh sách cũ.
             */
            log.warn(
                    "Could not refresh devices: {}",
                    ex.getMessage()
            );
        }
    }
    /*
     * Chạy mỗi 5 giây.
     * Phương thức này không gọi device-service.
     * Nó chỉ dùng danh sách devices đang nằm trong RAM.
     */
    @Scheduled(
            initialDelayString =
                    "${simulation.job-initial-delay-ms:3000}",
            fixedDelayString =
                    "${simulation.interval-ms:5000}"
    )
    public void simulate(){
        for(DeviceSimulationTargetResponse device : devices){
            /*
             * Chưa thể mô phỏng nếu thiết bị không có
             * công suất định mức.
             */
            if (device.ratedPowerW() == null
                    || device.ratedPowerW().signum() <= 0) {
                continue;
            }
            TelemetryReading reading =
                    generateReading(device);

            EnergyUsageEvent event =
                    createEvent(device, reading);

            publish(event);


        }
    }
    private TelemetryReading generateReading(DeviceSimulationTargetResponse device){
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 25% khả năng thiết bị đang tắt
        boolean poweredOn =
                random.nextDouble() >= 0.25;
        // Nếu bật: sử dụng từ 35% đến gần 100% công suất
        double utilization = poweredOn
                ? random.nextDouble(0.35, 1.0)
                : 0.0;
        BigDecimal averagePowerW = device.ratedPowerW()
                .multiply(BigDecimal.valueOf(utilization))
                .setScale(3, RoundingMode.HALF_UP);
        BigDecimal intervalEnergyWh = averagePowerW
                .multiply(BigDecimal.valueOf(intervalMillis))
                .divide(
                        MILLISECONDS_PER_HOUR,
                        6,
                        RoundingMode.HALF_UP
                );
        return new TelemetryReading(
                UUID.randomUUID(),
                device.deviceUid(),
                averagePowerW,
                intervalEnergyWh,
                Instant.now()
        );

    }
    private EnergyUsageEvent createEvent(DeviceSimulationTargetResponse device, TelemetryReading reading){
        return new EnergyUsageEvent(
                reading.readingId(),
                1,
                reading.deviceUid(),
                device.userId(),
                device.deviceType(),
                reading.averagePowerW(),
                reading.intervalEnergyWh(),
                reading.measuredAt(),
                Instant.now(),
                ReadingSource.SIMULATOR
        );
    }
    private void publish(EnergyUsageEvent event) {
        if (!isValidEvent(event)) {
            log.warn(
                    "Skipping invalid energy event: eventId={}, deviceUid={}",
                    event == null ? null : event.eventId(),
                    event == null ? null : event.deviceUid()
            );
            return;
        }

        try {
            kafkaTemplate.send(
                    topic,
                    event.deviceUid().toString(),
                    event
            ).whenComplete((result, exception) -> {
                if (exception != null) {
                    logPublishFailure(event, exception);
                    return;
                }

                if (result != null) {
                    log.debug(
                            "Energy event published: eventId={}, partition={}, offset={}",
                            event.eventId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset()
                    );
                }
            });
        } catch (RuntimeException exception) {
            // Serialization, metadata lookup or a full producer buffer may fail synchronously.
            logPublishFailure(event, exception);
        }
    }

    private void logPublishFailure(
            EnergyUsageEvent event,
            Throwable exception
    ) {
        log.error(
                "Failed to publish energy event: eventId={}, deviceUid={}, topic={}",
                event.eventId(),
                event.deviceUid(),
                topic,
                exception
        );
    }

    private boolean isValidEvent(EnergyUsageEvent event) {
        return event != null
                && event.eventId() != null
                && event.schemaVersion() > 0
                && event.deviceUid() != null
                && event.userId() != null
                && event.deviceType() != null
                && !event.deviceType().isBlank()
                && event.averagePowerW() != null
                && event.averagePowerW().signum() >= 0
                && event.intervalEnergyWh() != null
                && event.intervalEnergyWh().signum() >= 0
                && event.measuredAt() != null
                && event.receivedAt() != null
                && !event.receivedAt().isBefore(event.measuredAt())
                && event.source() != null;
    }
}
