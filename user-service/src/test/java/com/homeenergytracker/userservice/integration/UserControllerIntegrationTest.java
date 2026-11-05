package com.homeenergytracker.userservice.integration;

import com.homeenergytracker.userservice.entity.User;
import com.homeenergytracker.userservice.repository.UserRepository;
import com.homeenergytracker.userservice.security.UserDetails.CustomUserDetails;
import com.homeenergytracker.userservice.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "internal.user-service-token=test-internal-token")
@AutoConfigureMockMvc
class UserControllerIntegrationTest {

    private static final KeyPair JWT_KEYS = generateRsaKeyPair();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "jwt.private-key",
                () -> Base64.getEncoder().encodeToString(
                        JWT_KEYS.getPrivate().getEncoded()
                )
        );
        registry.add(
                "jwt.public-key",
                () -> Base64.getEncoder().encodeToString(
                        JWT_KEYS.getPublic().getEncoded()
                )
        );
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void getCurrentUser_shouldUseUserIdFromJwt() throws Exception {
        User currentUser = userRepository.saveAndFlush(
                createUserEntity("current@example.com")
        );
        userRepository.saveAndFlush(createUserEntity("other@example.com"));

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(currentUser.getId()))
                .andExpect(jsonPath("$.data.email").value("current@example.com"));
    }

    @Test
    void currentUserEndpoints_shouldReturnUnauthorized_withoutJwt() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCurrentUser_shouldReturnNotFound_whenJwtUserDoesNotExist() throws Exception {
        User deletedUser = userRepository.saveAndFlush(
                createUserEntity("deleted@example.com")
        );
        String token = bearerToken(deletedUser);
        userRepository.deleteById(deletedUser.getId());
        userRepository.flush();

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_EXISTS"));
    }

    @Test
    void updateCurrentUser_shouldOnlyUpdateJwtOwner() throws Exception {
        User currentUser = userRepository.saveAndFlush(
                createUserEntity("current@example.com")
        );
        User otherUser = userRepository.saveAndFlush(
                createUserEntity("other@example.com")
        );

        String requestBody = """
                {
                  "firstName": "Updated Kiet"
                }
                """;

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(currentUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(currentUser.getId()))
                .andExpect(jsonPath("$.data.firstName").value("Updated Kiet"));

        assertThat(userRepository.findById(currentUser.getId()).orElseThrow()
                .getFirstName()).isEqualTo("Updated Kiet");
        assertThat(userRepository.findById(otherUser.getId()).orElseThrow()
                .getFirstName()).isEqualTo("Kiet");
    }

    @Test
    void deleteCurrentUser_shouldOnlyDeleteJwtOwner() throws Exception {
        User currentUser = userRepository.saveAndFlush(
                createUserEntity("current@example.com")
        );
        User otherUser = userRepository.saveAndFlush(
                createUserEntity("other@example.com")
        );

        mockMvc.perform(delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(userRepository.existsById(currentUser.getId())).isFalse();
        assertThat(userRepository.existsById(otherUser.getId())).isTrue();
    }

    @Test
    void legacyUserEndpoints_shouldNotBeAvailable() throws Exception {
        User currentUser = userRepository.saveAndFlush(
                createUserEntity("current@example.com")
        );
        User otherUser = userRepository.saveAndFlush(
                createUserEntity("other@example.com")
        );
        String authorization = bearerToken(currentUser);

        mockMvc.perform(get("/api/v1/users/{id}", otherUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/users/{id}", otherUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .isIn(404, 405));

        mockMvc.perform(delete("/api/v1/users/{id}", otherUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .isIn(404, 405));

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .isIn(404, 405));

        assertThat(userRepository.existsById(currentUser.getId())).isTrue();
        assertThat(userRepository.existsById(otherUser.getId())).isTrue();
    }

    private String bearerToken(User user) {
        return "Bearer " + jwtService.generateToken(new CustomUserDetails(user));
    }

    private User createUserEntity(String email) {
        User user = new User();
        user.setFirstName("Kiet");
        user.setLastName("Nguyen");
        user.setEmail(email);
        user.setPasswordHash("test-password-hash");
        user.setAddress("Ho Chi Minh City");
        return user;
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Could not generate test RSA key pair",
                    exception
            );
        }
    }
}
