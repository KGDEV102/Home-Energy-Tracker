package com.homeenergytracker.usageservice.consumer;

import com.homeenergytracker.usageservice.event.EnergyUsageEvent;
import com.homeenergytracker.usageservice.event.ReadingSource;
import com.homeenergytracker.usageservice.exception.InvalidEnergyUsageEventException;
import com.homeenergytracker.usageservice.repository.EnergyUsageRepository;
import com.homeenergytracker.usageservice.validation.EnergyUsageEventValidator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EnergyUsageConsumerTest {
    private static final UUID DEVICE_UID =
            UUID.fromString("47e16e47-808c-4ba0-92c4-91fc1a470fd4");

    private static final String KAFKA_KEY =
            DEVICE_UID.toString();
    @Mock
    private EnergyUsageEventValidator validator;

    @Mock
    private EnergyUsageRepository repository;

    @InjectMocks
    private EnergyUsageConsumer consumer;

    private EnergyUsageEvent validEvent() {
        Instant measuredAt =
                Instant.parse("2026-09-26T03:00:00Z");

        return new EnergyUsageEvent(
                UUID.fromString(
                        "83179ef8-90b8-4fbf-99c6-83ace668990f"
                ),
                1,
                DEVICE_UID,
                1L,
                "AIR_CONDITIONER",
                new BigDecimal("1200.50"),
                new BigDecimal("1.67"),
                measuredAt,
                measuredAt.plusSeconds(1),
                ReadingSource.SIMULATOR
        );
    }
    private ConsumerRecord<String, EnergyUsageEvent> consumerRecord(
            EnergyUsageEvent event
    ) {
        return new ConsumerRecord<>(
                "energy-usage.v1",
                0,
                0L,
                KAFKA_KEY,
                event
        );
    }
    @Test
    void shouldValidateAndSaveEvent() {
        EnergyUsageEvent event = validEvent();
        ConsumerRecord<String, EnergyUsageEvent> record =
                consumerRecord(event);

        consumer.consume(record);

        /*
         * Kiểm tra consumer gọi validator trước,
         * sau đó mới gọi repository.
         */
        InOrder order = inOrder(validator, repository);

        order.verify(validator)
                .validate(KAFKA_KEY, event);

        order.verify(repository)
                .save(event);
    }

    @Test
    void shouldNotSaveWhenValidationFails() {
        EnergyUsageEvent event = validEvent();
        ConsumerRecord<String, EnergyUsageEvent> record =
                consumerRecord(event);

        InvalidEnergyUsageEventException expectedException =
                new InvalidEnergyUsageEventException(
                        "Event is invalid"
                );

        doThrow(expectedException)
                .when(validator)
                .validate(KAFKA_KEY, event);

        InvalidEnergyUsageEventException actualException =
                assertThrows(
                        InvalidEnergyUsageEventException.class,
                        () -> consumer.consume(record)
                );

        assertSame(expectedException, actualException);

        verify(repository, never())
                .save(any(EnergyUsageEvent.class));
    }
    @Test
    void shouldPropagateRepositoryException() {
        EnergyUsageEvent event = validEvent();
        ConsumerRecord<String, EnergyUsageEvent> record =
                consumerRecord(event);

        RuntimeException expectedException =
                new RuntimeException("InfluxDB unavailable");

        doThrow(expectedException)
                .when(repository)
                .save(event);

        RuntimeException actualException =
                assertThrows(
                        RuntimeException.class,
                        () -> consumer.consume(record)
                );

        assertSame(expectedException, actualException);

        verify(validator)
                .validate(KAFKA_KEY, event);

        verify(repository)
                .save(event);
    }


}
