package com.homeenergytracker.usageservice.repository;

import com.homeenergytracker.common.event.EnergyUsageEvent;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class InfluxEnergyUsageRepository implements EnergyUsageRepository {

    private final WriteApiBlocking writeApi;
    private final String bucket;
    private final String org;
    public InfluxEnergyUsageRepository(
            InfluxDBClient influxDBClient,
            @Value("${influxdb.bucket}") String bucket,
            @Value("${influxdb.org}") String org
    ){
        this.writeApi = influxDBClient.getWriteApiBlocking();
        this.bucket=bucket;
        this.org=org;
    }
    @Override
    public void save(EnergyUsageEvent event){
        Point point = Point
                .measurement("energy_usage")
                .addTag(
                        "device_uid",
                        event.deviceUid().toString()
                )
                .addTag(
                        "user_id",
                        event.userId().toString()
                )
                .addTag(
                        "device_type",
                        event.deviceType()
                )
                .addTag(
                        "source",
                        event.source().name()
                )
                .addTag(
                        "event_id",
                        event.eventId().toString()
                )
                .addField(
                        "schema_version",
                        event.schemaVersion()
                )
                .addField(
                        "average_power_w",
                        event.averagePowerW()
                )
                .addField(
                        "interval_energy_wh",
                        event.intervalEnergyWh()
                )
                .addField(
                        "received_at",
                        event.receivedAt().toString()
                )
                .time(
                        event.measuredAt(),
                        WritePrecision.NS
                );

        writeApi.writePoint(
                bucket,
                org,
                point
        );
    }

}
