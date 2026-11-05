package com.homeenergytracker.notificationservice.mail;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpMailGateway {

    private final JavaMailSender mailSender;

    public SmtpMailGateway(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @CircuitBreaker(
            name = "smtpMail",
            fallbackMethod = "smtpFallback"
    )
    public void send(SimpleMailMessage message) {
        mailSender.send(message);
    }

    private void smtpFallback(
            SimpleMailMessage message,
            Throwable cause
    ) {
        throw new MailSendException(
                "SMTP provider is temporarily unavailable",
                cause
        );
    }
}
