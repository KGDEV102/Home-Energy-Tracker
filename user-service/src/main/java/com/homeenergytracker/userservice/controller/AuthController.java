package com.homeenergytracker.userservice.controller;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.userservice.dto.LoginRequest;
import com.homeenergytracker.userservice.dto.LoginResponse;
import com.homeenergytracker.userservice.dto.RefreshTokenResponse;
import com.homeenergytracker.userservice.dto.RegisterRequest;
import com.homeenergytracker.userservice.dto.UserDto;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import com.homeenergytracker.userservice.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final long refreshTokenExpirationMillis;
    private final boolean secureCookie;

    public AuthController(
            AuthService authService,
            @Value("${refresh-token.expiration}") long refreshTokenExpirationMillis,
            @Value("${auth.cookie.secure}") boolean secureCookie
    ) {
        this.authService = authService;
        this.refreshTokenExpirationMillis = refreshTokenExpirationMillis;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/register")
    @Operation(security = {})
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<UserDto> register(@Valid @RequestBody RegisterRequest request) {
        return SuccessResponse.of(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(security = {})
    public ResponseEntity<SuccessResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) throws UnknownHostException {
        AuthService.LoginResult result = authService.login(
                request,
                InetAddress.getByName(httpRequest.getRemoteAddr()),
                httpRequest.getHeader(HttpHeaders.USER_AGENT)
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(result.refreshToken()).toString())
                .body(SuccessResponse.of(result.response()));
    }

    @PostMapping("/refresh")
    @Operation(security = {})
    public ResponseEntity<SuccessResponse<RefreshTokenResponse>> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken
    ) {
        AuthService.RefreshResult result = authService.refresh(refreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(result.refreshToken()).toString())
                .body(SuccessResponse.of(result.response()));
    }

    private ResponseCookie refreshTokenCookie(String refreshToken) {
        return ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Duration.ofMillis(refreshTokenExpirationMillis))
                .build();
    }
}
