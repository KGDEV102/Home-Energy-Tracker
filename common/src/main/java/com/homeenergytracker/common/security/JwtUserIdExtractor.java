package com.homeenergytracker.common.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

public final class JwtUserIdExtractor {

    private JwtUserIdExtractor() {
    }

    public static Long extract(Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
            throw unauthorized("JWT subject is missing");
        }

        try {
            long userId = Long.parseLong(jwt.getSubject());
            if (userId <= 0) {
                throw unauthorized("JWT subject must be a positive numeric user ID");
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw unauthorized("JWT subject must be a positive numeric user ID");
        }
    }

    private static ResponseStatusException unauthorized(String reason) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, reason);
    }
}
