package com.homeenergytracker.userservice.service;

import com.homeenergytracker.userservice.dto.LoginRequest;
import com.homeenergytracker.userservice.dto.LoginResponse;
import com.homeenergytracker.userservice.dto.RefreshTokenResponse;
import com.homeenergytracker.userservice.dto.RegisterRequest;
import com.homeenergytracker.userservice.dto.UserDto;
import com.homeenergytracker.userservice.entity.RefreshToken;
import com.homeenergytracker.userservice.entity.RefreshTokenRevocationReason;
import com.homeenergytracker.userservice.entity.User;
import com.homeenergytracker.userservice.exception.EmailAlreadyExistsException;
import com.homeenergytracker.userservice.exception.InvalidCredentialsException;
import com.homeenergytracker.userservice.exception.InvalidRefreshTokenException;
import com.homeenergytracker.userservice.mapper.UserMapper;
import com.homeenergytracker.userservice.repository.RefreshTokenRepository;
import com.homeenergytracker.userservice.repository.UserRepository;
import com.homeenergytracker.userservice.security.UserDetails.CustomUserDetails;
import com.homeenergytracker.userservice.security.jwt.JwtService;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final long accessTokenExpirationMillis;
    private final long refreshTokenExpirationMillis;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            @Value("${jwt.expiration}") long accessTokenExpirationMillis,
            @Value("${refresh-token.expiration}") long refreshTokenExpirationMillis
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.accessTokenExpirationMillis = accessTokenExpirationMillis;
        this.refreshTokenExpirationMillis = refreshTokenExpirationMillis;
    }

    @Transactional
    public LoginResult login(
            LoginRequest request,
            InetAddress clientIp,
            String userAgent
    ) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(),
                            request.password()
                    )
            );
        } catch (AuthenticationException exception) {
            throw new InvalidCredentialsException();
        }

        CustomUserDetails principal =
                (CustomUserDetails) authentication.getPrincipal();
        User user = principal.getUser();
        Instant now = Instant.now();

        String accessToken = jwtService.generateToken(principal);
        String rawRefreshToken = generateRefreshToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setFamilyId(UUID.randomUUID());
        refreshToken.setTokenHash(hashRefreshToken(rawRefreshToken));
        refreshToken.setIssuedAt(now);
        refreshToken.setExpiresAt(
                now.plusMillis(refreshTokenExpirationMillis)
        );
        refreshToken.setCreatedIp(clientIp);
        refreshToken.setUserAgent(limitUserAgent(userAgent));

        refreshTokenRepository.save(refreshToken);

        LoginResponse response = new LoginResponse(
                userMapper.toDto(user),
                accessToken,
                "Bearer",
                accessTokenExpirationMillis / 1000
        );

        return new LoginResult(response, rawRefreshToken);
    }

    @Transactional
    public RefreshResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        RefreshToken currentToken = refreshTokenRepository
                .findByTokenHashForUpdate(hashRefreshToken(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        Instant now = Instant.now();
        if (!currentToken.isActive(now)) {
            if (currentToken.isUsed()) {
                currentToken.setRevokedAt(now);
                currentToken.setRevocationReason(
                        RefreshTokenRevocationReason.REUSE_DETECTED
                );
                refreshTokenRepository.revokeActiveTokensByFamilyId(
                        currentToken.getFamilyId(),
                        now,
                        RefreshTokenRevocationReason.REUSE_DETECTED
                );
            }
            throw new InvalidRefreshTokenException();
        }

        User user = currentToken.getUser();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        if (!userDetails.isEnabled()) {
            refreshTokenRepository.revokeActiveTokensByFamilyId(
                    currentToken.getFamilyId(),
                    now,
                    RefreshTokenRevocationReason.USER_DISABLED
            );
            throw new InvalidRefreshTokenException();
        }

        String newRawRefreshToken = generateRefreshToken();
        RefreshToken nextToken = new RefreshToken();
        nextToken.setUser(user);
        nextToken.setFamilyId(currentToken.getFamilyId());
        nextToken.setTokenHash(hashRefreshToken(newRawRefreshToken));
        nextToken.setIssuedAt(now);
        nextToken.setExpiresAt(
                now.plusMillis(refreshTokenExpirationMillis)
        );
        nextToken.setCreatedIp(currentToken.getCreatedIp());
        nextToken.setUserAgent(currentToken.getUserAgent());
        refreshTokenRepository.saveAndFlush(nextToken);

        currentToken.setUsedAt(now);
        currentToken.setRevokedAt(now);
        currentToken.setRevocationReason(RefreshTokenRevocationReason.ROTATED);
        currentToken.setReplacedByToken(nextToken);

        RefreshTokenResponse response = new RefreshTokenResponse(
                jwtService.generateToken(userDetails),
                "Bearer",
                accessTokenExpirationMillis / 1000
        );
        return new RefreshResult(response, newRawRefreshToken);
    }

    @Transactional
    public UserDto register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException();
        }

        User user = new User();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setAddress(request.address());
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        try {
            User saved = userRepository.saveAndFlush(user);
            return userMapper.toDto(saved);
        } catch (DataIntegrityViolationException exception) {
            // The database also guards against concurrent registrations.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint
                        && "users_email_key".equals(constraint.getConstraintName())) {
                    throw new EmailAlreadyExistsException();
                }
            }
            throw exception;
        }
    }

    private String generateRefreshToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    private String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    exception
            );
        }
    }

    private String limitUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return userAgent.length() <= 512
                ? userAgent
                : userAgent.substring(0, 512);
    }

    public record LoginResult(
            LoginResponse response,
            String refreshToken
    ) {
    }

    public record RefreshResult(
            RefreshTokenResponse response,
            String refreshToken
    ) {
    }
}
