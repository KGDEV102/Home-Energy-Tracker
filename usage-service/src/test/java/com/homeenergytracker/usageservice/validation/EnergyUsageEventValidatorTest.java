package com.homeenergytracker.usageservice.validation;

import com.homeenergytracker.common.event.EnergyUsageEvent;
import com.homeenergytracker.common.event.ReadingSource;
import com.homeenergytracker.usageservice.exception.InvalidEnergyUsageEventException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class EnergyUsageEventValidatorTest {
    private static final UUID DEVICE_UID =
            UUID.fromString("47e16e47-808c-4ba0-92c4-91fc1a470fd4");

    private static final String KAFKA_KEY =
            DEVICE_UID.toString();

    private static final Instant MEASURED_AT =
            Instant.parse("2026-09-26T03:00:00Z");
    private final EnergyUsageEventValidator validator =
            new EnergyUsageEventValidator();
    private static EventBuilder validEvent() {
        return new EventBuilder();
    }

    /*
     * Test fixture giúp mỗi test chỉ thay đổi đúng trường
     * mà nó muốn kiểm tra.
     */
    private static final class EventBuilder {

        private UUID eventId =
                UUID.fromString("83179ef8-90b8-4fbf-99c6-83ace668990f");

        private int schemaVersion = 1;
        private UUID deviceUid = DEVICE_UID;
        private Long userId = 1L;
        private String deviceType = "AIR_CONDITIONER";

        private BigDecimal averagePowerW =
                new BigDecimal("1200.50");

        private BigDecimal intervalEnergyWh =
                new BigDecimal("1.67");

        private Instant measuredAt = MEASURED_AT;
        private Instant receivedAt = MEASURED_AT.plusSeconds(1);
        private ReadingSource source = ReadingSource.SIMULATOR;

        EventBuilder eventId(UUID eventId) {
            this.eventId = eventId;
            return this;
        }

        EventBuilder schemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        EventBuilder deviceUid(UUID deviceUid) {
            this.deviceUid = deviceUid;
            return this;
        }

        EventBuilder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        EventBuilder deviceType(String deviceType) {
            this.deviceType = deviceType;
            return this;
        }

        EventBuilder averagePowerW(BigDecimal averagePowerW) {
            this.averagePowerW = averagePowerW;
            return this;
        }

        EventBuilder intervalEnergyWh(BigDecimal intervalEnergyWh) {
            this.intervalEnergyWh = intervalEnergyWh;
            return this;
        }

        EventBuilder measuredAt(Instant measuredAt) {
            this.measuredAt = measuredAt;
            return this;
        }

        EventBuilder receivedAt(Instant receivedAt) {
            this.receivedAt = receivedAt;
            return this;
        }

        EventBuilder source(ReadingSource source) {
            this.source = source;
            return this;
        }

        EnergyUsageEvent build() {
            return new EnergyUsageEvent(
                    eventId,
                    schemaVersion,
                    deviceUid,
                    userId,
                    deviceType,
                    averagePowerW,
                    intervalEnergyWh,
                    measuredAt,
                    receivedAt,
                    source
            );
        }



    }
    @Test
    void shouldAcceptValidEvent() {
        EnergyUsageEvent event = validEvent().build();

        assertDoesNotThrow(
                () -> validator.validate(KAFKA_KEY, event)
        );
    }
    @Test
    void shouldAcceptZeroPowerAndEnergy() {
        EnergyUsageEvent event = validEvent()
                .averagePowerW(BigDecimal.ZERO)
                .intervalEnergyWh(BigDecimal.ZERO)
                .build();

        assertDoesNotThrow(
                () -> validator.validate(KAFKA_KEY, event)
        );
    }
    @Test
    void shouldAcceptEqualMeasuredAndReceivedTime() {
        EnergyUsageEvent event = validEvent()
                .receivedAt(MEASURED_AT)
                .build();

        assertDoesNotThrow(
                () -> validator.validate(KAFKA_KEY, event)
        );
    }
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidEvents")
    void shouldRejectInvalidEvent(
            String testName,
            String kafkaKey,
            EnergyUsageEvent event,
            String expectedMessage
    ) {
        InvalidEnergyUsageEventException exception =
                assertThrows(
                        InvalidEnergyUsageEventException.class,
                        () -> validator.validate(kafkaKey, event)
                );

        assertEquals(
                "INVALID_ENERGY_USAGE_EVENT",
                exception.getCode()
        );

        assertEquals(
                expectedMessage,
                exception.getMessage()
        );
    }

    static Stream<Arguments> invalidEvents() {
        return Stream.of(
                Arguments.of(
                        "event is null",
                        KAFKA_KEY,
                        null,
                        "Event must not be null"
                ),
                Arguments.of(
                        "eventId is missing",
                        KAFKA_KEY,
                        validEvent().eventId(null).build(),
                        "eventId is required"
                ),
                Arguments.of(
                        "schema version is unsupported",
                        KAFKA_KEY,
                        validEvent().schemaVersion(2).build(),
                        "Unsupported schemaVersion: 2"
                ),
                Arguments.of(
                        "deviceUid is missing",
                        KAFKA_KEY,
                        validEvent().deviceUid(null).build(),
                        "deviceUid is required"
                ),
                Arguments.of(
                        "Kafka key is null",
                        null,
                        validEvent().build(),
                        "Kafka key is required"
                ),
                Arguments.of(
                        "Kafka key is empty",
                        "",
                        validEvent().build(),
                        "Kafka key is required"
                ),
                Arguments.of(
                        "Kafka key is blank",
                        "   ",
                        validEvent().build(),
                        "Kafka key is required"
                ),
                Arguments.of(
                        "Kafka key does not match deviceUid",
                        UUID.randomUUID().toString(),
                        validEvent().build(),
                        "Kafka key does not match deviceUid"
                ),
                Arguments.of(
                        "userId is missing",
                        KAFKA_KEY,
                        validEvent().userId(null).build(),
                        "userId is required"
                ),
                Arguments.of(
                        "deviceType is null",
                        KAFKA_KEY,
                        validEvent().deviceType(null).build(),
                        "deviceType is required"
                ),
                Arguments.of(
                        "deviceType is blank",
                        KAFKA_KEY,
                        validEvent().deviceType("   ").build(),
                        "deviceType is required"
                ),
                Arguments.of(
                        "averagePowerW is null",
                        KAFKA_KEY,
                        validEvent().averagePowerW(null).build(),
                        "averagePowerW must be greater than or equal to zero"
                ),
                Arguments.of(
                        "averagePowerW is negative",
                        KAFKA_KEY,
                        validEvent()
                                .averagePowerW(new BigDecimal("-0.01"))
                                .build(),
                        "averagePowerW must be greater than or equal to zero"
                ),
                Arguments.of(
                        "intervalEnergyWh is null",
                        KAFKA_KEY,
                        validEvent().intervalEnergyWh(null).build(),
                        "intervalEnergyWh must be greater than or equal to zero"
                ),
                Arguments.of(
                        "intervalEnergyWh is negative",
                        KAFKA_KEY,
                        validEvent()
                                .intervalEnergyWh(new BigDecimal("-0.01"))
                                .build(),
                        "intervalEnergyWh must be greater than or equal to zero"
                ),
                Arguments.of(
                        "measuredAt is missing",
                        KAFKA_KEY,
                        validEvent().measuredAt(null).build(),
                        "measuredAt is required"
                ),
                Arguments.of(
                        "receivedAt is missing",
                        KAFKA_KEY,
                        validEvent().receivedAt(null).build(),
                        "receivedAt is required"
                ),
                Arguments.of(
                        "receivedAt is before measuredAt",
                        KAFKA_KEY,
                        validEvent()
                                .receivedAt(MEASURED_AT.minusSeconds(1))
                                .build(),
                        "receivedAt must not be before measuredAt"
                ),
                Arguments.of(
                        "source is missing",
                        KAFKA_KEY,
                        validEvent().source(null).build(),
                        "source is required"
                )
        );
    }


}
