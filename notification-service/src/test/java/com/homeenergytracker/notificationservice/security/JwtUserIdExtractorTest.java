package com.homeenergytracker.notificationservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUserIdExtractorTest {

    private final JwtUserIdExtractor extractor = new JwtUserIdExtractor();

    @Test
    void shouldExtractNumericUserIdFromSubject() {
        Jwt jwt = jwtWithSubject("42");

        Long userId = extractor.extract(jwt);

        assertEquals(42L, userId);
    }

    @Test
    void shouldRejectMissingSubject() {
        Jwt jwt = jwtWithSubject(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> extractor.extract(jwt)
        );

        assertEquals(401, exception.getStatusCode().value());
    }

    @Test
    void shouldRejectNonNumericSubject() {
        Jwt jwt = jwtWithSubject("user@example.com");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> extractor.extract(jwt)
        );

        assertEquals(401, exception.getStatusCode().value());
    }

    private Jwt jwtWithSubject(String subject) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuedAt(Instant.parse("2026-09-27T00:00:00Z"))
                .expiresAt(Instant.parse("2026-09-27T01:00:00Z"));

        if (subject != null) {
            builder.subject(subject);
        }

        return builder.build();
    }
}
