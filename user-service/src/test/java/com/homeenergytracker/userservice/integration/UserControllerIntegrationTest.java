package com.homeenergytracker.userservice.integration;

import com.homeenergytracker.userservice.entity.User;
import com.homeenergytracker.userservice.entity.UserStatus;
import com.homeenergytracker.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void createUser_shouldPersistUserAndReturnSuccess() throws Exception {
        String requestBody = """
                {
                  "firstName": "Kiet",
                  "lastName": "Nguyen",
                  "email": "kiet@example.com",
                  "address": "Ho Chi Minh City"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.firstName").value("Kiet"))
                .andExpect(jsonPath("$.data.email").value("kiet@example.com"));

        assertThat(userRepository.count()).isEqualTo(1);

        User savedUser = userRepository.findAll().getFirst();
        assertThat(savedUser.getFirstName()).isEqualTo("Kiet");
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();
    }

    @Test
    void createUser_shouldReturnBadRequest_whenRequestIsInvalid() throws Exception {
        String requestBody = """
                {
                  "firstName": "",
                  "lastName": "",
                  "email": "invalid-email"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details.firstName").exists())
                .andExpect(jsonPath("$.error.details.lastName").exists())
                .andExpect(jsonPath("$.error.details.email").exists())
                .andExpect(jsonPath("$.statusCode").value(400));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void createUser_shouldReturnConflict_whenEmailAlreadyExists() throws Exception {
        userRepository.saveAndFlush(createUserEntity());

        String requestBody = """
                {
                  "firstName": "Another",
                  "lastName": "User",
                  "email": "kiet@example.com"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.statusCode").value(409));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void getUser_shouldReturnUser_whenUserExists() throws Exception {
        User savedUser = userRepository.saveAndFlush(createUserEntity());

        mockMvc.perform(get("/api/v1/users/{id}", savedUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(savedUser.getId()))
                .andExpect(jsonPath("$.data.email").value("kiet@example.com"));
    }

    @Test
    void getUser_shouldReturnNotFound_whenUserDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_NOT_EXISTS"))
                .andExpect(jsonPath("$.statusCode").value(404));
    }

    @Test
    void updateUser_shouldUpdateOnlyProvidedFields() throws Exception {
        User savedUser = userRepository.saveAndFlush(createUserEntity());

        String requestBody = """
                {
                  "firstName": "Updated Kiet"
                }
                """;

        mockMvc.perform(patch("/api/v1/users/{id}", savedUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Updated Kiet"))
                .andExpect(jsonPath("$.data.lastName").value("Nguyen"))
                .andExpect(jsonPath("$.data.email").value("kiet@example.com"));

        User updatedUser = userRepository.findById(savedUser.getId()).orElseThrow();
        assertThat(updatedUser.getFirstName()).isEqualTo("Updated Kiet");
        assertThat(updatedUser.getLastName()).isEqualTo("Nguyen");
        assertThat(updatedUser.getEmail()).isEqualTo("kiet@example.com");
    }

    @Test
    void deleteUser_shouldRemoveUserFromDatabase() throws Exception {
        User savedUser = userRepository.saveAndFlush(createUserEntity());

        mockMvc.perform(delete("/api/v1/users/{id}", savedUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(userRepository.existsById(savedUser.getId())).isFalse();
    }

    private User createUserEntity() {
        User user = new User();
        user.setFirstName("Kiet");
        user.setLastName("Nguyen");
        user.setEmail("kiet@example.com");
        user.setAddress("Ho Chi Minh City");
        return user;
    }
}
