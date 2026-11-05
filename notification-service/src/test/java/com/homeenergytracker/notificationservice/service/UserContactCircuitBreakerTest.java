package com.homeenergytracker.notificationservice.service;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.notificationservice.client.UserServiceClient;
import com.homeenergytracker.notificationservice.dto.UserNotificationContactResponse;
import com.homeenergytracker.notificationservice.exception.UserServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserContactCircuitBreakerTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            CircuitBreakerAutoConfiguration.class
                    ))
                    .withUserConfiguration(AopTestConfiguration.class)
                    .withPropertyValues(
                            "resilience4j.circuitbreaker.instances.userService.sliding-window-size=10",
                            "resilience4j.circuitbreaker.instances.userService.minimum-number-of-calls=5",
                            "resilience4j.circuitbreaker.instances.userService.failure-rate-threshold=50",
                            "resilience4j.circuitbreaker.instances.userService.record-exceptions[0]="
                                    + "com.homeenergytracker.notificationservice.exception."
                                    + "UserServiceUnavailableException",
                            "resilience4j.circuitbreaker.instances.userService.ignore-exceptions[0]="
                                    + "org.springframework.web.client."
                                    + "HttpClientErrorException"
                    );

    @Test
    void repeatedFailures_shouldOpenCircuitAndSkipUserServiceClient() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            UserContactService service =
                    context.getBean(UserContactService.class);
            CountingUserServiceClient client =
                    context.getBean(CountingUserServiceClient.class);
            CircuitBreaker circuitBreaker = context
                    .getBean(CircuitBreakerRegistry.class)
                    .circuitBreaker("userService");

            for (int attempt = 0; attempt < 5; attempt++) {
                assertThatThrownBy(() -> service.getNotificationContact(1L))
                        .isInstanceOf(UserServiceUnavailableException.class);
            }

            assertThat(circuitBreaker.getState())
                    .isEqualTo(CircuitBreaker.State.OPEN);

            assertThatThrownBy(() -> service.getNotificationContact(1L))
                    .isInstanceOf(UserServiceUnavailableException.class)
                    .hasCauseInstanceOf(CallNotPermittedException.class);
            assertThat(client.callCount()).isEqualTo(5);
        });
    }

    @Test
    void repeatedClientErrors_shouldNotOpenCircuit() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            UserContactService service =
                    context.getBean(UserContactService.class);
            CountingUserServiceClient client =
                    context.getBean(CountingUserServiceClient.class);
            CircuitBreaker circuitBreaker = context
                    .getBean(CircuitBreakerRegistry.class)
                    .circuitBreaker("userService");
            client.rejectWithUnauthorized();

            for (int attempt = 0; attempt < 5; attempt++) {
                assertThatThrownBy(() -> service.getNotificationContact(1L))
                        .isInstanceOf(HttpClientErrorException.Unauthorized.class);
            }

            assertThat(circuitBreaker.getState())
                    .isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls())
                    .isZero();
            assertThat(client.callCount()).isEqualTo(5);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class AopTestConfiguration {

        @Bean
        CountingUserServiceClient userServiceClient() {
            return new CountingUserServiceClient();
        }

        @Bean
        UserContactService userContactService(
                CountingUserServiceClient userServiceClient
        ) {
            return new UserContactService(
                    userServiceClient,
                    "test-internal-token"
            );
        }
    }

    static class CountingUserServiceClient implements UserServiceClient {
        private int callCount;
        private boolean unauthorized;

        @Override
        public SuccessResponse<UserNotificationContactResponse>
        getNotificationContact(Long userId, String internalToken) {
            callCount++;
            if (unauthorized) {
                throw HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED,
                        "Internal token rejected",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        null
                );
            }
            throw new ResourceAccessException("user-service unavailable");
        }

        void rejectWithUnauthorized() {
            unauthorized = true;
        }

        int callCount() {
            return callCount;
        }
    }
}
