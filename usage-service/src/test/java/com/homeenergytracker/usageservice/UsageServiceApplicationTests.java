package com.homeenergytracker.usageservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "influxdb.token=test-token")
class UsageServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
