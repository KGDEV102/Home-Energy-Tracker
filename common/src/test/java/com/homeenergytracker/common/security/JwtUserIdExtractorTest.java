package com.homeenergytracker.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUserIdExtractorTest {

    @Test
    void extractsNumericUserIdFromSubject() {
        assertEquals(42L, JwtUserIdExtractor.extract(jwtWithSubject("42")));
    }

    @Test
    void rejectsMissingSubject() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> JwtUserIdExtractor.extract(jwtWithSubject(null))
        );

        assertEquals(401, exception.getStatusCode().value());
    }

    @Test
    void rejectsNonNumericSubject() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> JwtUserIdExtractor.extract(jwtWithSubject("user@example.com"))
        );

        assertEquals(401, exception.getStatusCode().value());
    }

    @Test
    void rejectsNonPositiveSubject() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> JwtUserIdExtractor.extract(jwtWithSubject("0"))
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
