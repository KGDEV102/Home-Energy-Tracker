package com.homeenergytracker.ingestionservice.client;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.ingestionservice.dto.DeviceSimulationTargetResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceSimulationTargetGatewayTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            CircuitBreakerAutoConfiguration.class
                    ))
                    .withUserConfiguration(AopTestConfiguration.class)
                    .withPropertyValues(
                            "resilience4j.circuitbreaker.instances.deviceService.sliding-window-size=10",
                            "resilience4j.circuitbreaker.instances.deviceService.minimum-number-of-calls=5",
                            "resilience4j.circuitbreaker.instances.deviceService.failure-rate-threshold=50",
                            "resilience4j.circuitbreaker.instances.deviceService.record-exceptions[0]="
                                    + "com.homeenergytracker.ingestionservice.exception."
                                    + "DeviceServiceUnavailableException"
                    );

    @Test
    void repeatedFailures_shouldOpenCircuitAndSkipDeviceServiceClient() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            DeviceSimulationTargetGateway gateway = context.getBean(
                    DeviceSimulationTargetGateway.class
            );
            CountingDeviceServiceClient client = context.getBean(
                    CountingDeviceServiceClient.class
            );
            CircuitBreaker circuitBreaker = context
                    .getBean(CircuitBreakerRegistry.class)
                    .circuitBreaker("deviceService");

            for (int attempt = 0; attempt < 5; attempt++) {
                assertThat(gateway.loadTargets()).isEqualTo(Optional.empty());
            }

            assertThat(circuitBreaker.getState())
                    .isEqualTo(CircuitBreaker.State.OPEN);

            assertThat(gateway.loadTargets()).isEqualTo(Optional.empty());
            assertThat(client.callCount()).isEqualTo(5);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class AopTestConfiguration {

        @Bean
        CountingDeviceServiceClient deviceServiceClient() {
            return new CountingDeviceServiceClient();
        }

        @Bean
        DeviceSimulationTargetGateway deviceSimulationTargetGateway(
                CountingDeviceServiceClient deviceServiceClient
        ) {
            return new DeviceSimulationTargetGateway(
                    deviceServiceClient,
                    "test-internal-token"
            );
        }
    }

    static class CountingDeviceServiceClient implements DeviceServiceClient {
        private int callCount;

        @Override
        public SuccessResponse<List<DeviceSimulationTargetResponse>>
        getSimulationTargets(String serviceToken) {
            callCount++;
            throw new ResourceAccessException("device-service unavailable");
        }

        int callCount() {
            return callCount;
        }
    }
}
