package com.homeenergytracker.usageservice.repository;

import com.homeenergytracker.common.event.EnergyUsageEvent;
import com.homeenergytracker.common.event.ReadingSource;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InfluxEnergyUsageRepositoryTest {
    private static final String BUCKET = "energy-usage";
    private static final String ORG = "home-energy-tracker";
    private static final UUID EVENT_ID =
            UUID.fromString("83179ef8-90b8-4fbf-99c6-83ace668990f");

    private static final UUID DEVICE_UID =
            UUID.fromString("47e16e47-808c-4ba0-92c4-91fc1a470fd4");

    private static final Instant MEASURED_AT =
            Instant.parse("2026-09-26T03:00:00Z");
    @Mock
    private InfluxDBClient influxDBClient;

    @Mock
    private WriteApiBlocking writeApi;

    private InfluxEnergyUsageRepository repository;

    @BeforeEach
    void setUp() {
        /*
         * Khi repository gọi getWriteApiBlocking(),
         * trả về WriteApiBlocking giả do Mockito tạo.
         */
        when(influxDBClient.getWriteApiBlocking())
                .thenReturn(writeApi);

        repository = new InfluxEnergyUsageRepository(
                influxDBClient,
                BUCKET,
                ORG
        );
    }

    private EnergyUsageEvent validEvent() {
        return new EnergyUsageEvent(
                EVENT_ID,
                1,
                DEVICE_UID,
                1L,
                "AIR_CONDITIONER",
                new BigDecimal("1200.50"),
                new BigDecimal("1.67"),
                MEASURED_AT,
                MEASURED_AT.plusSeconds(1),
                ReadingSource.SIMULATOR
        );
    }

    private BigInteger toNanoseconds(Instant instant) {
        return BigInteger.valueOf(instant.getEpochSecond())
                .multiply(BigInteger.valueOf(1_000_000_000L))
                .add(BigInteger.valueOf(instant.getNano()));
    }
    @Test
    void shouldWriteEnergyUsagePoint() {
        // Arrange
        EnergyUsageEvent event = validEvent();

        // Act
        repository.save(event);

        /*
         * Lấy Point được truyền vào writePoint()
         * để kiểm tra dữ liệu bên trong.
         */
        ArgumentCaptor<Point> pointCaptor =
                ArgumentCaptor.forClass(Point.class);

        verify(writeApi).writePoint(
                eq(BUCKET),
                eq(ORG),
                pointCaptor.capture()
        );

        Point point = pointCaptor.getValue();

        // InfluxDB Point không có getMeasurement(),
        // nên kiểm tra measurement qua line protocol.
        assertThat(point.toLineProtocol())
                .startsWith("energy_usage,");

        assertThat(point.getTags())
                .containsEntry(
                        "device_uid",
                        DEVICE_UID.toString()
                )
                .containsEntry(
                        "user_id",
                        "1"
                )
                .containsEntry(
                        "device_type",
                        "AIR_CONDITIONER"
                )
                .containsEntry(
                        "source",
                        "SIMULATOR"
                );

        assertThat(point.getFields())
                .containsEntry(
                        "event_id",
                        EVENT_ID.toString()
                )
                .containsEntry(
                        "schema_version",
                        1L
                )
                .containsEntry(
                        "average_power_w",
                        new BigDecimal("1200.50")
                )
                .containsEntry(
                        "interval_energy_wh",
                        new BigDecimal("1.67")
                )
                .containsEntry(
                        "received_at",
                        MEASURED_AT.plusSeconds(1).toString()
                );

        assertThat(point.getPrecision())
                .isEqualTo(WritePrecision.NS);

        assertThat(point.getTime())
                .isEqualTo(toNanoseconds(MEASURED_AT));
    }

    @Test
    void shouldPropagateExceptionWhenWriteFails() {
        // Arrange
        EnergyUsageEvent event = validEvent();

        RuntimeException expectedException =
                new RuntimeException("InfluxDB unavailable");

        doThrow(expectedException)
                .when(writeApi)
                .writePoint(
                        eq(BUCKET),
                        eq(ORG),
                        any(Point.class)
                );

        // Act
        RuntimeException actualException =
                assertThrows(
                        RuntimeException.class,
                        () -> repository.save(event)
                );

        // Assert
        assertSame(expectedException, actualException);

        verify(writeApi).writePoint(
                eq(BUCKET),
                eq(ORG),
                any(Point.class)
        );
    }


}
