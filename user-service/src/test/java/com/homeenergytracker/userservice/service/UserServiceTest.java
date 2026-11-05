package com.homeenergytracker.userservice.service;

import com.homeenergytracker.userservice.dto.UpdateUser;
import com.homeenergytracker.userservice.dto.UserDto;
import com.homeenergytracker.userservice.entity.User;
import com.homeenergytracker.userservice.entity.UserStatus;
import com.homeenergytracker.userservice.exception.EmailAlreadyExistsException;
import com.homeenergytracker.userservice.exception.UserNotExists;
import com.homeenergytracker.userservice.mapper.UserMapper;
import com.homeenergytracker.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "kiet@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    @Test
    void createUser_shouldReturnCreatedUser_whenEmailDoesNotExist() {
        // Arrange
        UserDto request = createRequest();
        User entity = createEntity();
        UserDto expectedResponse = createResponse();

        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(entity);
        when(userRepository.save(entity)).thenReturn(entity);
        when(userMapper.toDto(entity)).thenReturn(expectedResponse);

        // Act
        UserDto actualResponse = userService.createUser(request);

        // Assert
        assertThat(actualResponse).isEqualTo(expectedResponse);
        verify(userRepository).existsByEmail(EMAIL);
        verify(userMapper).toEntity(request);
        verify(userRepository).save(entity);
        verify(userMapper).toDto(entity);
    }

    @Test
    void createUser_shouldThrowException_whenEmailAlreadyExists() {
        // Arrange
        UserDto request = createRequest();
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessage("Email already exists");

        verify(userMapper, never()).toEntity(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_shouldThrowException_whenUserDoesNotExist() {
        // Arrange
        UpdateUser request = new UpdateUser("New name", null, null, null, null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> userService.updateUser(USER_ID, request))
                .isInstanceOf(UserNotExists.class)
                .hasMessage("User not exists");

        verify(userMapper, never()).updateEntity(any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_shouldThrowException_whenEmailBelongsToAnotherUser() {
        // Arrange
        User existingUser = createEntity();
        UpdateUser request = new UpdateUser(
                null,
                null,
                "other@example.com",
                null,
                null
        );

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmailAndIdNot("other@example.com", USER_ID))
                .thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> userService.updateUser(USER_ID, request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessage("Email already exists");

        verify(userMapper, never()).updateEntity(any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_shouldUpdateOtherFields_withoutCheckingEmail_whenEmailIsNull() {
        // Arrange
        User existingUser = createEntity();
        UpdateUser request = new UpdateUser(
                "Updated Kiet",
                null,
                null,
                "Ha Noi",
                null
        );
        UserDto expectedResponse = createResponse();

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);
        when(userMapper.toDto(existingUser)).thenReturn(expectedResponse);

        // Act
        UserDto actualResponse = userService.updateUser(USER_ID, request);

        // Assert
        assertThat(actualResponse).isEqualTo(expectedResponse);
        verify(userRepository, never()).existsByEmailAndIdNot(any(), any());
        verify(userMapper).updateEntity(request, existingUser);
        verify(userRepository).save(existingUser);
        verify(userMapper).toDto(existingUser);
    }

    @Test
    void updateUser_shouldSucceed_whenKeepingCurrentEmail() {
        // Arrange
        User existingUser = createEntity();
        UpdateUser request = new UpdateUser(null, null, EMAIL, null, null);
        UserDto expectedResponse = createResponse();

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmailAndIdNot(EMAIL, USER_ID)).thenReturn(false);
        when(userRepository.save(existingUser)).thenReturn(existingUser);
        when(userMapper.toDto(existingUser)).thenReturn(expectedResponse);

        // Act
        UserDto actualResponse = userService.updateUser(USER_ID, request);

        // Assert
        assertThat(actualResponse).isEqualTo(expectedResponse);
        verify(userRepository).existsByEmailAndIdNot(EMAIL, USER_ID);
        verify(userMapper).updateEntity(request, existingUser);
        verify(userRepository).save(existingUser);
    }

    @Test
    void getUserById_shouldReturnUser_whenUserExists() {
        // Arrange
        User user = createEntity();
        UserDto expectedResponse = createResponse();

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(expectedResponse);

        // Act
        UserDto actualResponse = userService.getUserById(USER_ID);

        // Assert
        assertThat(actualResponse).isEqualTo(expectedResponse);
        verify(userMapper).toDto(user);
    }

    @Test
    void getUserById_shouldThrowException_whenUserDoesNotExist() {
        // Arrange
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> userService.getUserById(USER_ID))
                .isInstanceOf(UserNotExists.class)
                .hasMessage("User not exists");

        verify(userMapper, never()).toDto(any());
    }

    @Test
    void deleteUserById_shouldDeleteUser_whenUserExists() {
        // Arrange
        User user = createEntity();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        // Act
        userService.deleteUserById(USER_ID);

        // Assert
        verify(userRepository).delete(user);
    }

    @Test
    void deleteUserById_shouldThrowException_whenUserDoesNotExist() {
        // Arrange
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> userService.deleteUserById(USER_ID))
                .isInstanceOf(UserNotExists.class)
                .hasMessage("User not exists");

        verify(userRepository, never()).delete(any());
    }

    private UserDto createRequest() {
        return new UserDto(
                null,
                "Kiet",
                "Nguyen",
                EMAIL,
                "Ho Chi Minh City",
                null,
                null,
                null
        );
    }

    private User createEntity() {
        User user = new User();
        user.setId(USER_ID);
        user.setFirstName("Kiet");
        user.setLastName("Nguyen");
        user.setEmail(EMAIL);
        user.setAddress("Ho Chi Minh City");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private UserDto createResponse() {
        Instant now = Instant.parse("2026-09-19T00:00:00Z");
        return new UserDto(
                USER_ID,
                "Kiet",
                "Nguyen",
                EMAIL,
                "Ho Chi Minh City",
                UserStatus.ACTIVE,
                now,
                now
        );
    }

    @org.junit.jupiter.api.Nested
    class JwtSecurityChecks {
        private final java.security.KeyPair keys = newKeys();

        private java.security.KeyPair newKeys() {
            try {
                var generator = java.security.KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                return generator.generateKeyPair();
            } catch (java.security.GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        }

        private com.homeenergytracker.userservice.security.jwt.JwtService jwtService() {
            return new com.homeenergytracker.userservice.security.jwt.JwtService(
                    java.util.Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()),
                    java.util.Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()),
                    3_600_000L);
        }

        @Test
        void validRsaJwtUsesUserIdAndContinuesWithAuthenticatedContext() throws Exception {
            var jwt = jwtService();
            var user = new com.homeenergytracker.userservice.security.UserDetails.CustomUserDetails(createEntity());
            String token = jwt.generateToken(user);
            assertThat(jwt.extractAllClaims(token).getSubject()).isEqualTo(USER_ID.toString());

            var request = new org.springframework.mock.web.MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + token);
            var response = new org.springframework.mock.web.MockHttpServletResponse();
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            try {
                new com.homeenergytracker.userservice.security.jwt.JwtAuthenticationFilter(jwt)
                        .doFilter(request, response, (req, res) -> {
                            calls.incrementAndGet();
                            var auth = org.springframework.security.core.context.SecurityContextHolder
                                    .getContext().getAuthentication();
                            assertThat(auth.isAuthenticated()).isTrue();
                            assertThat(auth.getPrincipal()).isEqualTo(USER_ID);
                            assertThat(auth.getAuthorities()).isEmpty();
                        });
                assertThat(calls.get()).isEqualTo(1);
            } finally {
                org.springframework.security.core.context.SecurityContextHolder.clearContext();
            }
        }

        @Test
        void invalidExpiredAndNonNumericTokensReturn401WithoutContinuing() throws Exception {
            var jwt = jwtService();
            String expired = io.jsonwebtoken.Jwts.builder().subject("1")
                    .expiration(java.util.Date.from(Instant.now().minusSeconds(120)))
                    .signWith(keys.getPrivate(), io.jsonwebtoken.Jwts.SIG.RS256).compact();
            String nonNumeric = io.jsonwebtoken.Jwts.builder().subject(EMAIL)
                    .expiration(java.util.Date.from(Instant.now().plusSeconds(3600)))
                    .signWith(keys.getPrivate(), io.jsonwebtoken.Jwts.SIG.RS256).compact();
            String forged = io.jsonwebtoken.Jwts.builder().subject("1")
                    .expiration(java.util.Date.from(Instant.now().plusSeconds(3600)))
                    .signWith(newKeys().getPrivate(), io.jsonwebtoken.Jwts.SIG.RS256).compact();
            for (String token : java.util.List.of("invalid", expired, nonNumeric, forged)) {
                var request = new org.springframework.mock.web.MockHttpServletRequest();
                request.addHeader("Authorization", "Bearer " + token);
                var response = new org.springframework.mock.web.MockHttpServletResponse();
                new com.homeenergytracker.userservice.security.jwt.JwtAuthenticationFilter(jwt)
                        .doFilter(request, response, (req, res) -> {
                            throw new AssertionError("Invalid JWT must not reach the next filter");
                        });
                assertThat(response.getStatus()).isEqualTo(401);
                assertThat(org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication()).isNull();
            }
        }

        @Test
        void requestWithoutBearerContinuesWithoutAuthentication() throws Exception {
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            new com.homeenergytracker.userservice.security.jwt.JwtAuthenticationFilter(jwtService())
                    .doFilter(new org.springframework.mock.web.MockHttpServletRequest(),
                            new org.springframework.mock.web.MockHttpServletResponse(),
                            (req, res) -> calls.incrementAndGet());
            assertThat(calls.get()).isEqualTo(1);
            assertThat(org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication()).isNull();
        }

        @Test
        void securityChainPermitsLoginRequiresJwtAndProtectsInternalEndpoints() throws Exception {
            try (var context = new org.springframework.web.context.support.AnnotationConfigWebApplicationContext()) {
                context.setServletContext(new org.springframework.mock.web.MockServletContext());
                context.register(SecurityTestConfiguration.class,
                        com.homeenergytracker.userservice.security.SecurityConfig.class);
                context.refresh();
                var filter = context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class);
                for (String path : java.util.List.of("/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/users/me")) {
                    var request = new org.springframework.mock.web.MockHttpServletRequest("POST", path);
                    request.setServletPath(path);
                    var response = new org.springframework.mock.web.MockHttpServletResponse();
                    var calls = new java.util.concurrent.atomic.AtomicInteger();
                    filter.doFilter(request, response, (req, res) -> calls.incrementAndGet());
                    if (path.startsWith("/api/v1/auth/")) {
                        assertThat(calls.get()).isEqualTo(1);
                        assertThat(response.getStatus()).isEqualTo(200);
                    } else {
                        assertThat(calls.get()).isZero();
                        assertThat(response.getStatus()).isEqualTo(401);
                    }
                }
                String internalPath = "/internal/v1/users/1/notification-contact";
                for (String token : java.util.List.of("", "wrong-token", "test-internal-token")) {
                    var request = new org.springframework.mock.web.MockHttpServletRequest(
                            "GET", internalPath
                    );
                    request.setServletPath(internalPath);
                    if (!token.isEmpty()) {
                        request.addHeader("X-Internal-Token", token);
                    }
                    var response = new org.springframework.mock.web.MockHttpServletResponse();
                    var calls = new java.util.concurrent.atomic.AtomicInteger();
                    filter.doFilter(request, response, (req, res) -> calls.incrementAndGet());
                    if (token.equals("test-internal-token")) {
                        assertThat(calls.get()).isEqualTo(1);
                        assertThat(response.getStatus()).isEqualTo(200);
                    } else {
                        assertThat(calls.get()).isZero();
                        assertThat(response.getStatus()).isEqualTo(401);
                    }
                }
                var registration = context.getBean("jwtFilterRegistration",
                        org.springframework.boot.web.servlet.FilterRegistrationBean.class);
                assertThat(registration.isEnabled()).isFalse();
            }
        }
    }

    @org.springframework.context.annotation.Configuration
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    static class SecurityTestConfiguration {
        @org.springframework.context.annotation.Bean
        com.homeenergytracker.userservice.security.jwt.JwtAuthenticationFilter jwtFilter() {
            return new com.homeenergytracker.userservice.security.jwt.JwtAuthenticationFilter(
                    org.mockito.Mockito.mock(com.homeenergytracker.userservice.security.jwt.JwtService.class));
        }

        @org.springframework.context.annotation.Bean
        com.homeenergytracker.userservice.security.InternalServiceAuthenticationFilter internalServiceFilter() {
            return new com.homeenergytracker.userservice.security.InternalServiceAuthenticationFilter(
                    "test-internal-token"
            );
        }

        @org.springframework.context.annotation.Bean
        com.homeenergytracker.userservice.security.UserDetails.CustomUserDetailsService userDetailsService() {
            return org.mockito.Mockito.mock(
                    com.homeenergytracker.userservice.security.UserDetails.CustomUserDetailsService.class);
        }
    }
}
