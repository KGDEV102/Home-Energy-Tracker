package com.homeenergytracker.userservice.service;

import com.homeenergytracker.userservice.entity.RefreshToken;
import com.homeenergytracker.userservice.entity.RefreshTokenRevocationReason;
import com.homeenergytracker.userservice.entity.User;
import com.homeenergytracker.userservice.exception.InvalidRefreshTokenException;
import com.homeenergytracker.userservice.mapper.UserMapper;
import com.homeenergytracker.userservice.repository.RefreshTokenRepository;
import com.homeenergytracker.userservice.repository.UserRepository;
import com.homeenergytracker.userservice.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenTest {

    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final AuthService service = new AuthService(
            mock(UserRepository.class),
            refreshTokenRepository,
            Mappers.getMapper(UserMapper.class),
            new BCryptPasswordEncoder(),
            mock(AuthenticationManager.class),
            jwtService,
            900_000L,
            2_592_000_000L
    );

    @Test
    void refreshRotatesTokenAndKeepsItsFamily() {
        RefreshToken currentToken = activeToken();
        when(refreshTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.of(currentToken));
        when(refreshTokenRepository.saveAndFlush(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any())).thenReturn("new-access-jwt");

        AuthService.RefreshResult result = service.refresh("current-raw-refresh-token");

        ArgumentCaptor<RefreshToken> nextTokenCaptor =
                ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).saveAndFlush(nextTokenCaptor.capture());

        RefreshToken nextToken = nextTokenCaptor.getValue();
        assertThat(nextToken.getFamilyId()).isEqualTo(currentToken.getFamilyId());
        assertThat(nextToken.getTokenHash()).hasSize(64)
                .isNotEqualTo(result.refreshToken());
        assertThat(currentToken.getUsedAt()).isNotNull();
        assertThat(currentToken.getRevokedAt()).isNotNull();
        assertThat(currentToken.getRevocationReason())
                .isEqualTo(RefreshTokenRevocationReason.ROTATED);
        assertThat(currentToken.getReplacedByToken()).isSameAs(nextToken);
        assertThat(result.response().accessToken()).isEqualTo("new-access-jwt");
        assertThat(result.response().tokenType()).isEqualTo("Bearer");
        assertThat(result.response().expiresIn()).isEqualTo(900L);
    }

    @Test
    void reuseOfRotatedTokenRevokesItsActiveFamily() {
        RefreshToken currentToken = activeToken();
        Instant rotatedAt = Instant.now();
        currentToken.setUsedAt(rotatedAt);
        currentToken.setRevokedAt(rotatedAt);
        currentToken.setRevocationReason(RefreshTokenRevocationReason.ROTATED);

        when(refreshTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.of(currentToken));

        assertThatThrownBy(() -> service.refresh("reused-raw-refresh-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(currentToken.getRevocationReason())
                .isEqualTo(RefreshTokenRevocationReason.REUSE_DETECTED);
        verify(refreshTokenRepository).revokeActiveTokensByFamilyId(
                eq(currentToken.getFamilyId()),
                any(Instant.class),
                eq(RefreshTokenRevocationReason.REUSE_DETECTED)
        );
    }

    private RefreshToken activeToken() {
        User user = new User();
        user.setId(7L);
        user.setFirstName("Kiet");
        user.setLastName("Nguyen");
        user.setEmail("kiet@example.com");
        user.setPasswordHash("bcrypt-hash");
        user.prePersist();

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setFamilyId(UUID.randomUUID());
        token.setTokenHash("a".repeat(64));
        token.setIssuedAt(Instant.now().minusSeconds(60));
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }
}
