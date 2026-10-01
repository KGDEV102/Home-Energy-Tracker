package com.homeenergytracker.usageservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "influxdb.token=test-token",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/test-issuer"
})
class UsageServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
