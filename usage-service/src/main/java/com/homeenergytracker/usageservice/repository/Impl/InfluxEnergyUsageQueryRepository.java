package com.homeenergytracker.usageservice.repository.Impl;

import com.homeenergytracker.usageservice.dto.DeviceUsageResponse;
import com.homeenergytracker.usageservice.dto.UsageDataPointResponse;
import com.homeenergytracker.usageservice.dto.UsageSummaryResponse;
import com.homeenergytracker.usageservice.repository.EnergyUsageQueryRepository;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;


@Repository
public class InfluxEnergyUsageQueryRepository implements EnergyUsageQueryRepository {
    private static final Set<String> SUPPORTED_WINDOWS =
            Set.of("5m", "15m", "1h", "1d");

    private final QueryApi queryApi;
    private final String bucket;
    private final String org;

    public InfluxEnergyUsageQueryRepository(
            InfluxDBClient influxDBClient,
            @Value("${influxdb.bucket}") String bucket,
            @Value("${influxdb.org}") String org
    ) {
        this.queryApi = influxDBClient.getQueryApi();
        this.bucket = bucket;
        this.org = org;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }

        if (value instanceof BigDecimal decimal) {
            return decimal;
        }

        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }

        throw new IllegalArgumentException(
                "Unsupported numeric value: " + value
        );
    }
    @Override
    public UsageSummaryResponse findSummary(
            Long userId,
            Instant from,
            Instant to
    ){
        String query = """
            base = from(bucket: "%s")
                |> range(
                    start: time(v: "%s"),
                    stop: time(v: "%s")
                )
                |> filter(fn: (r) =>
                    r["_measurement"] == "energy_usage" and
                    r["user_id"] == "%s"
                )

            totalEnergy = base
                |> filter(fn: (r) =>
                    r["_field"] == "interval_energy_wh"
                )
                |> group()
                |> sum(column: "_value")
                |> set(key: "metric", value: "total_energy")

            averagePower = base
                |> filter(fn: (r) =>
                    r["_field"] == "average_power_w"
                )
                |> group(columns: ["device_uid"])
                |> mean(column: "_value")
                |> group()
                |> sum(column: "_value")
                |> set(key: "metric", value: "average_power")

            maxPower = base
                |> filter(fn: (r) =>
                    r["_field"] == "average_power_w"
                )
                |> aggregateWindow(
                    every: 1m,
                    fn: mean,
                    createEmpty: false
                )
                |> group(columns: ["_time"])
                |> sum(column: "_value")
                |> group()
                |> max(column: "_value")
                |> set(key: "metric", value: "max_power")

            union(tables: [
                totalEnergy,
                averagePower,
                maxPower
            ])
            """.formatted(
                bucket,
                from,
                to,
                userId
        );
        List<FluxTable> tables = queryApi.query(query, org);
        BigDecimal totalEnergy = BigDecimal.ZERO;
        BigDecimal averagePower = BigDecimal.ZERO;
        BigDecimal maxPower = BigDecimal.ZERO;

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                String metric =
                        String.valueOf(record.getValueByKey("metric"));

                BigDecimal value = toBigDecimal(record.getValue());

                switch (metric) {
                    case "total_energy" -> totalEnergy = value;
                    case "average_power" -> averagePower = value;
                    case "max_power" -> maxPower = value;
                }
            }
        }

        return new UsageSummaryResponse(
                totalEnergy,
                averagePower,
                maxPower,
                from,
                to
        );

    }

    @Override
    public List<DeviceUsageResponse> findUsageByDevice(
            Long userId,
            Instant from,
            Instant to
    ) {
        String query = """
                base = from(bucket: "%s")
                    |> range(
                        start: time(v: "%s"),
                        stop: time(v: "%s")
                    )
                    |> filter(fn: (r) =>
                        r["_measurement"] == "energy_usage" and
                        r["user_id"] == "%s"
                    )

                energy = base
                    |> filter(fn: (r) =>
                        r["_field"] == "interval_energy_wh"
                    )
                    |> group(columns: ["device_uid", "device_type"])
                    |> sum(column: "_value")
                    |> set(key: "metric", value: "total_energy")

                averagePower = base
                    |> filter(fn: (r) =>
                        r["_field"] == "average_power_w"
                    )
                    |> group(columns: ["device_uid", "device_type"])
                    |> mean(column: "_value")
                    |> set(key: "metric", value: "average_power")

                maxPower = base
                    |> filter(fn: (r) =>
                        r["_field"] == "average_power_w"
                    )
                    |> group(columns: ["device_uid", "device_type"])
                    |> max(column: "_value")
                    |> set(key: "metric", value: "max_power")

                union(tables: [
                    energy,
                    averagePower,
                    maxPower
                ])
                """.formatted(
                bucket,
                from,
                to,
                userId
        );

        List<FluxTable> tables = queryApi.query(query, org);

        Set<DeviceKey> devices = new HashSet<>();
        Map<DeviceKey, BigDecimal> totalEnergyByDevice = new HashMap<>();
        Map<DeviceKey, BigDecimal> averagePowerByDevice = new HashMap<>();
        Map<DeviceKey, BigDecimal> maxPowerByDevice = new HashMap<>();

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Object deviceUidValue = record.getValueByKey("device_uid");
                Object deviceTypeValue = record.getValueByKey("device_type");

                if (deviceUidValue == null || deviceTypeValue == null) {
                    continue;
                }

                DeviceKey key = new DeviceKey(
                        UUID.fromString(deviceUidValue.toString()),
                        deviceTypeValue.toString()
                );

                devices.add(key);

                String metric = String.valueOf(
                        record.getValueByKey("metric")
                );
                BigDecimal value = toBigDecimal(record.getValue());

                switch (metric) {
                    case "total_energy" ->
                            totalEnergyByDevice.put(key, value);
                    case "average_power" ->
                            averagePowerByDevice.put(key, value);
                    case "max_power" ->
                            maxPowerByDevice.put(key, value);
                    default -> {
                        // Ignore metrics that are not part of this response.
                    }
                }
            }
        }

        return devices.stream()
                .sorted(Comparator.comparing(
                        key -> key.deviceUid().toString()
                ))
                .map(key -> new DeviceUsageResponse(
                        key.deviceUid(),
                        key.deviceType(),
                        totalEnergyByDevice.getOrDefault(
                                key,
                                BigDecimal.ZERO
                        ),
                        averagePowerByDevice.getOrDefault(
                                key,
                                BigDecimal.ZERO
                        ),
                        maxPowerByDevice.getOrDefault(
                                key,
                                BigDecimal.ZERO
                        )
                ))
                .toList();
    }

    @Override
    public List<UsageDataPointResponse> findDeviceTimeSeries(
            Long userId,
            UUID deviceUid,
            Instant from,
            Instant to,
            String window
    ) {
        validateWindow(window);

        String query = """
                base = from(bucket: "%s")
                    |> range(
                        start: time(v: "%s"),
                        stop: time(v: "%s")
                    )
                    |> filter(fn: (r) =>
                        r["_measurement"] == "energy_usage" and
                        r["user_id"] == "%s" and
                        r["device_uid"] == "%s"
                    )

                energy = base
                    |> filter(fn: (r) =>
                        r["_field"] == "interval_energy_wh"
                    )
                    |> aggregateWindow(
                        every: %s,
                        fn: sum,
                        createEmpty: false
                    )
                    |> set(key: "metric", value: "energy")

                power = base
                    |> filter(fn: (r) =>
                        r["_field"] == "average_power_w"
                    )
                    |> aggregateWindow(
                        every: %s,
                        fn: mean,
                        createEmpty: false
                    )
                    |> set(key: "metric", value: "power")

                union(tables: [energy, power])
                    |> sort(columns: ["_time"])
                """.formatted(
                bucket,
                from,
                to,
                userId,
                deviceUid,
                window,
                window
        );

        List<FluxTable> tables = queryApi.query(query, org);

        Set<Instant> timestamps = new TreeSet<>();
        Map<Instant, BigDecimal> energyByTimestamp = new HashMap<>();
        Map<Instant, BigDecimal> powerByTimestamp = new HashMap<>();

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Instant timestamp = record.getTime();
                if (timestamp == null) {
                    continue;
                }

                timestamps.add(timestamp);

                String metric = String.valueOf(
                        record.getValueByKey("metric")
                );
                BigDecimal value = toBigDecimal(record.getValue());

                switch (metric) {
                    case "energy" ->
                            energyByTimestamp.put(timestamp, value);
                    case "power" ->
                            powerByTimestamp.put(timestamp, value);
                    default -> {
                        // Ignore metrics that are not part of this response.
                    }
                }
            }
        }

        return timestamps.stream()
                .map(timestamp -> new UsageDataPointResponse(
                        timestamp,
                        energyByTimestamp.getOrDefault(
                                timestamp,
                                BigDecimal.ZERO
                        ),
                        powerByTimestamp.getOrDefault(
                                timestamp,
                                BigDecimal.ZERO
                        )
                ))
                .toList();
    }

    private void validateWindow(String window) {
        if (!SUPPORTED_WINDOWS.contains(window)) {
            throw new IllegalArgumentException(
                    "Unsupported usage window: " + window
            );
        }
    }

    private record DeviceKey(
            UUID deviceUid,
            String deviceType
    ) {
    }


}
