package com.homeenergytracker.notificationservice.mail;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmtpMailGatewayTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            CircuitBreakerAutoConfiguration.class
                    ))
                    .withUserConfiguration(AopTestConfiguration.class)
                    .withBean(CountingMailSender.class)
                    .withBean(SmtpMailGateway.class)
                    .withPropertyValues(
                            "resilience4j.circuitbreaker.instances.smtpMail.sliding-window-size=10",
                            "resilience4j.circuitbreaker.instances.smtpMail.minimum-number-of-calls=5",
                            "resilience4j.circuitbreaker.instances.smtpMail.failure-rate-threshold=50",
                            "resilience4j.circuitbreaker.instances.smtpMail.record-exceptions[0]=org.springframework.mail.MailException"
                    );

    @Test
    void repeatedFailures_shouldOpenCircuitAndSkipMailSender() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            SmtpMailGateway gateway = context.getBean(SmtpMailGateway.class);
            CountingMailSender mailSender =
                    context.getBean(CountingMailSender.class);
            CircuitBreaker circuitBreaker = context
                    .getBean(CircuitBreakerRegistry.class)
                    .circuitBreaker("smtpMail");
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo("user@example.com");

            for (int attempt = 0; attempt < 5; attempt++) {
                assertThatThrownBy(() -> gateway.send(message))
                        .isInstanceOf(MailSendException.class);
            }

            assertThat(circuitBreaker.getState())
                    .isEqualTo(CircuitBreaker.State.OPEN);

            assertThatThrownBy(() -> gateway.send(message))
                    .isInstanceOf(MailSendException.class)
                    .hasCauseInstanceOf(CallNotPermittedException.class);
            assertThat(mailSender.sendCount()).isEqualTo(5);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class AopTestConfiguration {
    }

    static class CountingMailSender extends JavaMailSenderImpl {
        private int sendCount;

        @Override
        public void send(SimpleMailMessage message) throws MailException {
            sendCount++;
            throw new MailSendException("SMTP unavailable");
        }

        int sendCount() {
            return sendCount;
        }
    }
}
