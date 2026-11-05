package com.homeenergytracker.userservice.service;

import com.homeenergytracker.common.web.CommonExceptionHandler;
import com.homeenergytracker.userservice.controller.AuthController;
import com.homeenergytracker.userservice.dto.RegisterRequest;
import com.homeenergytracker.userservice.entity.User;
import com.homeenergytracker.userservice.exception.EmailAlreadyExistsException;
import com.homeenergytracker.userservice.exception.UserExceptionHandler;
import com.homeenergytracker.userservice.mapper.UserMapper;
import com.homeenergytracker.userservice.repository.UserRepository;
import com.homeenergytracker.userservice.repository.RefreshTokenRepository;
import com.homeenergytracker.userservice.security.SecurityConfig;
import com.homeenergytracker.userservice.security.UserDetails.CustomUserDetailsService;
import com.homeenergytracker.userservice.security.jwt.JwtAuthenticationFilter;
import com.homeenergytracker.userservice.security.jwt.JwtService;
import jakarta.servlet.Filter;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegisterTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final AuthService service = new AuthService(
            repository,
            mock(RefreshTokenRepository.class),
            Mappers.getMapper(UserMapper.class),
            encoder,
            mock(AuthenticationManager.class),
            mock(JwtService.class),
            3_600_000L,
            2_592_000_000L
    );

    private RegisterRequest request(String password) {
        return new RegisterRequest("Kiet", "Nguyen", "  KIET@example.com  ", password, "HCM");
    }

    private void successfulSave() {
        when(repository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(7L);
            user.prePersist();
            return user;
        });
    }

    @Test
    void registerStoresRealSaltedHashAndNormalizesEmail() {
        successfulSave();
        var result = service.register(request("StrongPassword123"));
        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(repository).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPasswordHash()).isNotEqualTo("StrongPassword123");
        assertThat(encoder.matches("StrongPassword123", saved.getPasswordHash())).isTrue();
        assertThat(encoder.matches("wrong", saved.getPasswordHash())).isFalse();
        assertThat(saved.getEmail()).isEqualTo("kiet@example.com");
        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.firstName()).isEqualTo("Kiet");
        assertThat(result.lastName()).isEqualTo("Nguyen");
        verify(repository).existsByEmail("kiet@example.com");
    }

    @Test
    void existingEmailIsRejectedBeforeSaving() {
        when(repository.existsByEmail("kiet@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.register(request("StrongPassword123")))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentEmailUniqueViolationMapsToConflict() {
        var constraint = new ConstraintViolationException("duplicate", new SQLException("duplicate", "23505"),
                "users_email_key");
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate", constraint));
        assertThatThrownBy(() -> service.register(request("StrongPassword123")))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void unrelatedIntegrityViolationIsNotReportedAsDuplicateEmail() {
        var constraint = new ConstraintViolationException("other", new SQLException("other", "23502"),
                "users_first_name_not_null");
        var failure = new DataIntegrityViolationException("other", constraint);
        when(repository.saveAndFlush(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.register(request("StrongPassword123"))).isSameAs(failure);
    }

    @Test
    void requestValidationRejectsMissingWeakAndOversizedUtf8Passwords() {
        try (var validator = new LocalValidatorFactoryBean()) {
            validator.afterPropertiesSet();
            for (String password : new String[]{null, "", "short", "a".repeat(73), "\u00e9".repeat(37)}) {
                assertThat(validator.validate(request(password))).as("password length validation").isNotEmpty();
            }
            assertThat(validator.validate(request("\u00e9".repeat(36)))).isEmpty();
            var missing = new RegisterRequest(null, "", "not-an-email", "StrongPassword123", null);
            assertThat(validator.validate(missing)).extracting(v -> v.getPropertyPath().toString())
                    .contains("firstName", "lastName", "email");
        }
    }

    @Test
    void requestToStringRedactsPassword() {
        assertThat(request("StrongPassword123").toString()).doesNotContain("StrongPassword123").contains("[REDACTED]");
    }

    @Test
    void publicRegisterEndpointReturns201WithoutSecretsThroughRealSecurityChain() throws Exception {
        successfulSave();
        try (var context = securityContext(); var validator = validator()) {
            mvc(context, validator).perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON).content(json("StrongPassword123")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(7))
                    .andExpect(jsonPath("$.data.email").value("kiet@example.com"))
                    .andExpect(jsonPath("$.data.password").doesNotExist())
                    .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("StrongPassword123"))));
        }
    }

    @Test
    void registerEndpointReturns409ForDuplicate() throws Exception {
        when(repository.existsByEmail("kiet@example.com")).thenReturn(true);
        try (var context = securityContext(); var validator = validator()) {
            mvc(context, validator).perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON).content(json("StrongPassword123")))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void registerEndpointRejectsInvalidPayloadBeforePersistence() throws Exception {
        try (var context = securityContext(); var validator = validator()) {
            MockMvc mvc = mvc(context, validator);
            for (String payload : new String[]{"{}", json("short"), json("\u00e9".repeat(37))}) {
                mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                        .andExpect(status().isBadRequest());
            }
            verifyNoInteractions(repository);
        }
    }

    private String json(String password) {
        return "{\"firstName\":\"Kiet\",\"lastName\":\"Nguyen\",\"email\":\"  KIET@example.com  \",\"password\":\""
                + password + "\"}";
    }

    private LocalValidatorFactoryBean validator() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }

    private AnnotationConfigWebApplicationContext securityContext() {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(SecurityBeans.class, SecurityConfig.class);
        context.refresh();
        return context;
    }

    private MockMvc mvc(AnnotationConfigWebApplicationContext context, LocalValidatorFactoryBean validator) {
        return MockMvcBuilders.standaloneSetup(new AuthController(
                        service,
                        2_592_000_000L,
                        false
                ))
                .setValidator(validator)
                .setControllerAdvice(new UserExceptionHandler(), new CommonExceptionHandler())
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @Configuration
    @EnableWebSecurity
    static class SecurityBeans {
        @Bean
        JwtAuthenticationFilter jwtFilter() {
            return new JwtAuthenticationFilter(mock(JwtService.class));
        }

        @Bean
        com.homeenergytracker.userservice.security.InternalServiceAuthenticationFilter internalServiceFilter() {
            return new com.homeenergytracker.userservice.security.InternalServiceAuthenticationFilter(
                    "test-internal-token"
            );
        }

        @Bean
        CustomUserDetailsService userDetailsService() {
            return mock(CustomUserDetailsService.class);
        }
    }
}
