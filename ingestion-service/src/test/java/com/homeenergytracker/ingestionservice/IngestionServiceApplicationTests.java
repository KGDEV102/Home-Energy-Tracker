package com.homeenergytracker.ingestionservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "device-service.internal-token=test-internal-token")
class IngestionServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
