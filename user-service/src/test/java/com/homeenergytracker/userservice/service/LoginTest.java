package com.homeenergytracker.userservice.service;

import com.homeenergytracker.userservice.dto.LoginRequest;
import com.homeenergytracker.userservice.entity.RefreshToken;
import com.homeenergytracker.userservice.entity.User;
import com.homeenergytracker.userservice.mapper.UserMapper;
import com.homeenergytracker.userservice.repository.RefreshTokenRepository;
import com.homeenergytracker.userservice.repository.UserRepository;
import com.homeenergytracker.userservice.security.UserDetails.CustomUserDetails;
import com.homeenergytracker.userservice.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.net.InetAddress;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final AuthService service = new AuthService(
            userRepository,
            refreshTokenRepository,
            Mappers.getMapper(UserMapper.class),
            new BCryptPasswordEncoder(),
            authenticationManager,
            jwtService,
            900_000L,
            2_592_000_000L
    );

    @Test
    void loginCreatesAccessTokenAndStoresOnlyHashOfRefreshToken() throws Exception {
        User user = activeUser();
        CustomUserDetails principal = new CustomUserDetails(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );

        when(authenticationManager.authenticate(any()))
                .thenReturn(authentication);
        when(jwtService.generateToken(principal)).thenReturn("access-jwt");

        var result = service.login(
                new LoginRequest("  KIET@example.com  ", "StrongPassword123"),
                InetAddress.getByName("127.0.0.1"),
                "test-agent"
        );

        ArgumentCaptor<UsernamePasswordAuthenticationToken> authenticationCaptor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(authenticationCaptor.capture());
        assertThat(authenticationCaptor.getValue().getPrincipal()).isEqualTo("kiet@example.com");

        ArgumentCaptor<RefreshToken> refreshTokenCaptor =
                ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());

        RefreshToken saved = refreshTokenCaptor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getFamilyId()).isNotNull();
        assertThat(saved.getTokenHash()).hasSize(64).isNotEqualTo(result.refreshToken());
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        assertThat(saved.getCreatedIp().getHostAddress()).isEqualTo("127.0.0.1");
        assertThat(saved.getUserAgent()).isEqualTo("test-agent");

        assertThat(result.response().accessToken()).isEqualTo("access-jwt");
        assertThat(result.response().tokenType()).isEqualTo("Bearer");
        assertThat(result.response().expiresIn()).isEqualTo(900L);
        assertThat(result.response().user().id()).isEqualTo(7L);
    }

    private User activeUser() {
        User user = new User();
        user.setId(7L);
        user.setFirstName("Kiet");
        user.setLastName("Nguyen");
        user.setEmail("kiet@example.com");
        user.setPasswordHash("bcrypt-hash");
        user.prePersist();
        return user;
    }
}
