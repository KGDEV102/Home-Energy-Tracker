package com.homeenergytracker.ingestionservice;

import com.homeenergytracker.ingestionservice.client.DeviceServiceClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.service.registry.ImportHttpServices;

@SpringBootApplication
@EnableScheduling
@ImportHttpServices(
        group = "device-service",
        types = DeviceServiceClient.class
)
public class IngestionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestionServiceApplication.class, args);
    }

}
