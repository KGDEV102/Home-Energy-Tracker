package com.homeenergytracker.usageservice.repository;

import com.homeenergytracker.usageservice.dto.DeviceUsageResponse;
import com.homeenergytracker.usageservice.dto.UsageDataPointResponse;
import com.homeenergytracker.usageservice.dto.UsageSummaryResponse;
import com.homeenergytracker.usageservice.repository.Impl.InfluxEnergyUsageQueryRepository;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InfluxEnergyUsageQueryRepositoryTest {

    private static final String BUCKET = "energy-usage";
    private static final String ORG = "home-energy-tracker";
    private static final long USER_ID = 7L;
    private static final Instant FROM =
            Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO =
            Instant.parse("2026-10-02T00:00:00Z");
    private static final UUID FIRST_DEVICE_UID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND_DEVICE_UID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private InfluxDBClient influxDBClient;

    @Mock
    private QueryApi queryApi;

    private InfluxEnergyUsageQueryRepository repository;

    @BeforeEach
    void setUp() {
        when(influxDBClient.getQueryApi()).thenReturn(queryApi);

        repository = new InfluxEnergyUsageQueryRepository(
                influxDBClient,
                BUCKET,
                ORG
        );
    }

    @Test
    void shouldMapSummaryMetrics() {
        stubQueryResult(
                metricRecord("max_power", 1480.0),
                metricRecord("total_energy", 27.5),
                metricRecord("average_power", 1250.0)
        );

        UsageSummaryResponse result =
                repository.findSummary(USER_ID, FROM, TO);

        assertThat(result.totalEnergyWh())
                .isEqualByComparingTo("27.5");
        assertThat(result.averagePowerW())
                .isEqualByComparingTo("1250.0");
        assertThat(result.maxPowerW())
                .isEqualByComparingTo("1480.0");
        assertThat(result.from()).isEqualTo(FROM);
        assertThat(result.to()).isEqualTo(TO);

        ArgumentCaptor<String> queryCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(queryApi).query(queryCaptor.capture(), eq(ORG));
        assertThat(queryCaptor.getValue())
                .contains("from(bucket: \"" + BUCKET + "\")")
                .contains("r[\"user_id\"] == \"" + USER_ID + "\"")
                .contains("|> group(columns: [\"device_uid\"])")
                .contains(FROM.toString())
                .contains(TO.toString());
    }

    @Test
    void shouldReturnZeroSummaryWhenInfluxHasNoRecords() {
        when(queryApi.query(anyString(), eq(ORG)))
                .thenReturn(List.of());

        UsageSummaryResponse result =
                repository.findSummary(USER_ID, FROM, TO);

        assertThat(result.totalEnergyWh()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.averagePowerW()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.maxPowerW()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldMergeMetricsByDeviceAndDefaultMissingMetricToZero() {
        stubQueryResult(
                deviceRecord(
                        FIRST_DEVICE_UID,
                        "AIR_CONDITIONER",
                        "total_energy",
                        500.0
                ),
                deviceRecord(
                        SECOND_DEVICE_UID,
                        "REFRIGERATOR",
                        "total_energy",
                        200.0
                ),
                deviceRecord(
                        FIRST_DEVICE_UID,
                        "AIR_CONDITIONER",
                        "average_power",
                        1000.0
                ),
                deviceRecord(
                        FIRST_DEVICE_UID,
                        "AIR_CONDITIONER",
                        "max_power",
                        1400.0
                ),
                deviceRecord(
                        SECOND_DEVICE_UID,
                        "REFRIGERATOR",
                        "average_power",
                        180.0
                )
        );

        List<DeviceUsageResponse> result =
                repository.findUsageByDevice(USER_ID, FROM, TO);

        assertThat(result).hasSize(2);

        DeviceUsageResponse firstDevice = result.get(0);
        assertThat(firstDevice.deviceUid()).isEqualTo(FIRST_DEVICE_UID);
        assertThat(firstDevice.deviceType()).isEqualTo("AIR_CONDITIONER");
        assertThat(firstDevice.totalEnergyWh()).isEqualByComparingTo("500.0");
        assertThat(firstDevice.averagePowerW()).isEqualByComparingTo("1000.0");
        assertThat(firstDevice.maxPowerW()).isEqualByComparingTo("1400.0");

        DeviceUsageResponse secondDevice = result.get(1);
        assertThat(secondDevice.deviceUid()).isEqualTo(SECOND_DEVICE_UID);
        assertThat(secondDevice.deviceType()).isEqualTo("REFRIGERATOR");
        assertThat(secondDevice.totalEnergyWh()).isEqualByComparingTo("200.0");
        assertThat(secondDevice.averagePowerW()).isEqualByComparingTo("180.0");
        assertThat(secondDevice.maxPowerW()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldMergeAndSortTimeSeriesMetricsByTimestamp() {
        Instant firstTimestamp = Instant.parse("2026-10-01T01:00:00Z");
        Instant secondTimestamp = Instant.parse("2026-10-01T02:00:00Z");

        stubQueryResult(
                timeSeriesRecord(secondTimestamp, "power", 500.0),
                timeSeriesRecord(firstTimestamp, "energy", 4.0),
                timeSeriesRecord(secondTimestamp, "energy", 5.0),
                timeSeriesRecord(firstTimestamp, "power", 200.0)
        );

        List<UsageDataPointResponse> result =
                repository.findDeviceTimeSeries(
                        USER_ID,
                        FIRST_DEVICE_UID,
                        FROM,
                        TO,
                        "1h"
                );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).timestamp()).isEqualTo(firstTimestamp);
        assertThat(result.get(0).energyWh()).isEqualByComparingTo("4.0");
        assertThat(result.get(0).averagePowerW()).isEqualByComparingTo("200.0");
        assertThat(result.get(1).timestamp()).isEqualTo(secondTimestamp);
        assertThat(result.get(1).energyWh()).isEqualByComparingTo("5.0");
        assertThat(result.get(1).averagePowerW()).isEqualByComparingTo("500.0");

        ArgumentCaptor<String> queryCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(queryApi).query(queryCaptor.capture(), eq(ORG));
        assertThat(queryCaptor.getValue())
                .contains("r[\"device_uid\"] == \"" + FIRST_DEVICE_UID + "\"")
                .contains("|> group()")
                .contains("every: 1h");
    }

    @Test
    void shouldRejectUnsupportedTimeSeriesWindowBeforeQueryingInflux() {
        assertThatThrownBy(() -> repository.findDeviceTimeSeries(
                USER_ID,
                FIRST_DEVICE_UID,
                FROM,
                TO,
                "2h"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported usage window: 2h");

        verifyNoInteractions(queryApi);
    }

    private FluxTable table(FluxRecord... records) {
        FluxTable table = mock(FluxTable.class);
        when(table.getRecords()).thenReturn(List.of(records));
        return table;
    }

    private void stubQueryResult(FluxRecord... records) {
        FluxTable resultTable = table(records);
        when(queryApi.query(anyString(), eq(ORG)))
                .thenReturn(List.of(resultTable));
    }

    private FluxRecord metricRecord(String metric, Number value) {
        FluxRecord record = mock(FluxRecord.class);
        when(record.getValueByKey("metric")).thenReturn(metric);
        when(record.getValue()).thenReturn(value);
        return record;
    }

    private FluxRecord deviceRecord(
            UUID deviceUid,
            String deviceType,
            String metric,
            Number value
    ) {
        FluxRecord record = metricRecord(metric, value);
        when(record.getValueByKey("device_uid"))
                .thenReturn(deviceUid.toString());
        when(record.getValueByKey("device_type"))
                .thenReturn(deviceType);
        return record;
    }

    private FluxRecord timeSeriesRecord(
            Instant timestamp,
            String metric,
            Number value
    ) {
        FluxRecord record = metricRecord(metric, value);
        when(record.getTime()).thenReturn(timestamp);
        return record;
    }
}
