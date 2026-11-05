package com.homeenergytracker.userservice.controller;

import com.homeenergytracker.userservice.dto.LoginResponse;
import com.homeenergytracker.userservice.dto.RefreshTokenResponse;
import com.homeenergytracker.userservice.dto.UserDto;
import com.homeenergytracker.userservice.entity.UserStatus;
import com.homeenergytracker.userservice.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import jakarta.servlet.http.Cookie;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerLoginTest {

    @Test
    void loginSetsRefreshTokenInHttpOnlyCookieAndDoesNotExposeItInBody() throws Exception {
        AuthService authService = mock(AuthService.class);
        AuthController controller = new AuthController(
                authService,
                2_592_000_000L,
                true
        );
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        LoginResponse response = new LoginResponse(
                new UserDto(
                        7L,
                        "Kiet",
                        "Nguyen",
                        "kiet@example.com",
                        null,
                        UserStatus.ACTIVE,
                        Instant.parse("2026-10-07T00:00:00Z"),
                        Instant.parse("2026-10-07T00:00:00Z")
                ),
                "access-jwt",
                "Bearer",
                900
        );
        when(authService.login(any(), any(), any()))
                .thenReturn(new AuthService.LoginResult(response, "raw-refresh-token"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"kiet@example.com\",\"password\":\"StrongPassword123\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().secure("refresh_token", true))
                .andExpect(jsonPath("$.data.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.data.user.id").value(7))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());

        verify(authService).login(any(), any(), any());
    }

    @Test
    void refreshRotatesCookieAndReturnsOnlyNewAccessToken() throws Exception {
        AuthService authService = mock(AuthService.class);
        AuthController controller = new AuthController(
                authService,
                2_592_000_000L,
                true
        );
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(authService.refresh("previous-refresh-token"))
                .thenReturn(new AuthService.RefreshResult(
                        new RefreshTokenResponse("new-access-jwt", "Bearer", 900),
                        "new-refresh-token"
                ));

        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "previous-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().secure("refresh_token", true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-jwt"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());

        verify(authService).refresh("previous-refresh-token");
    }
}
