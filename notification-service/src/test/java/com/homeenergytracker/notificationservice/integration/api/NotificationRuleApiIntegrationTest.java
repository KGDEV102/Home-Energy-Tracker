package com.homeenergytracker.notificationservice.integration.api;

import com.homeenergytracker.notificationservice.entity.NotificationRule;
import com.homeenergytracker.notificationservice.entity.NotificationType;
import com.homeenergytracker.notificationservice.repository.NotificationRepository;
import com.homeenergytracker.notificationservice.repository.NotificationRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureMockMvc
public class NotificationRuleApiIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer("postgres:17-alpine");
    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRuleRepository ruleRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanDatabase() {
        notificationRepository.deleteAll();
        ruleRepository.deleteAll();
    }

    private String authorizationFor(long userId) {
        String tokenValue = "token-" + userId;
        Instant now = Instant.now();

        Jwt jwt = Jwt.withTokenValue(tokenValue)
                .header("alg", "none")
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();

        when(jwtDecoder.decode(tokenValue)).thenReturn(jwt);

        return "Bearer " + tokenValue;
    }
    private NotificationRule saveRule(
            Long userId,
            UUID deviceUid
    ) {
        NotificationRule rule = new NotificationRule();

        rule.setUserId(userId);
        rule.setDeviceUid(deviceUid);
        rule.setType(NotificationType.POWER_THRESHOLD);
        rule.setThreshold(new BigDecimal("1000.000"));
        rule.setCooldownSeconds(300L);
        rule.setEnabled(true);

        return ruleRepository.saveAndFlush(rule);
    }

    @Test
    void shouldCreateNotificationRuleForAuthenticatedUser()
            throws Exception {

        // Arrange
        UUID deviceUid = UUID.randomUUID();

        String requestBody = """
                {
                  "deviceUid": "%s",
                  "type": "POWER_THRESHOLD",
                  "threshold": 1000.000,
                  "cooldownSeconds": 300,
                  "enabled": true
                }
                """.formatted(deviceUid);

        // Act + Assert HTTP response
        mockMvc.perform(post("/api/v1/notification-rules")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                authorizationFor(USER_ID)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.userId")
                        .value(USER_ID))
                .andExpect(jsonPath("$.data.deviceUid")
                        .value(deviceUid.toString()))
                .andExpect(jsonPath("$.data.type")
                        .value("POWER_THRESHOLD"))
                .andExpect(jsonPath("$.data.threshold")
                        .value(1000.000))
                .andExpect(jsonPath("$.data.cooldownSeconds")
                        .value(300))
                .andExpect(jsonPath("$.data.enabled")
                        .value(true));

        // Assert dữ liệu thật trong PostgreSQL
        assertThat(ruleRepository.findAll())
                .singleElement()
                .satisfies(rule -> {
                    assertThat(rule.getId()).isNotNull();
                    assertThat(rule.getUserId()).isEqualTo(USER_ID);
                    assertThat(rule.getDeviceUid())
                            .isEqualTo(deviceUid);
                    assertThat(rule.getType())
                            .isEqualTo(
                                    NotificationType.POWER_THRESHOLD
                            );
                    assertThat(rule.getThreshold())
                            .isEqualByComparingTo("1000.000");
                    assertThat(rule.getCooldownSeconds())
                            .isEqualTo(300L);
                    assertThat(rule.isEnabled()).isTrue();
                    assertThat(rule.getCreatedAt()).isNotNull();
                    assertThat(rule.getUpdatedAt()).isNotNull();
                });
    }



    @Test
    void shouldReturnPaginatedNotificationRules()
            throws Exception {

        // Arrange
        saveRule(USER_ID, UUID.randomUUID());
        saveRule(USER_ID, UUID.randomUUID());
        saveRule(USER_ID, UUID.randomUUID());

        // Act + Assert
        mockMvc.perform(get("/api/v1/notification-rules")
                        .param("page", "0")
                        .param("size", "2")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                authorizationFor(USER_ID)
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()")
                        .value(2))
                .andExpect(jsonPath("$.data.currentPage")
                        .value(0))
                .andExpect(jsonPath("$.data.pageSize")
                        .value(2))
                .andExpect(jsonPath("$.data.totalPage")
                        .value(2));
    }


    @Test
    void shouldUpdateNotificationRuleOwnedByAuthenticatedUser()
            throws Exception {

        // Arrange
        NotificationRule savedRule =
                saveRule(USER_ID, UUID.randomUUID());

        String requestBody = """
                {
                  "threshold": 1500.000,
                  "cooldownSeconds": 600,
                  "enabled": false
                }
                """;

        // Act + Assert HTTP response
        mockMvc.perform(patch(
                        "/api/v1/notification-rules/{id}",
                        savedRule.getId()
                )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                authorizationFor(USER_ID)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id")
                        .value(savedRule.getId()))
                .andExpect(jsonPath("$.data.threshold")
                        .value(1500.000))
                .andExpect(jsonPath("$.data.cooldownSeconds")
                        .value(600))
                .andExpect(jsonPath("$.data.enabled")
                        .value(false));

        // Assert database
        NotificationRule updatedRule =
                ruleRepository.findById(savedRule.getId())
                        .orElseThrow();

        assertThat(updatedRule.getThreshold())
                .isEqualByComparingTo("1500.000");
        assertThat(updatedRule.getCooldownSeconds())
                .isEqualTo(600L);
        assertThat(updatedRule.isEnabled()).isFalse();
    }
    @Test
    void shouldDeleteNotificationRuleOwnedByAuthenticatedUser()
            throws Exception {

        // Arrange
        NotificationRule savedRule =
                saveRule(USER_ID, UUID.randomUUID());

        // Act + Assert
        mockMvc.perform(delete(
                        "/api/v1/notification-rules/{id}",
                        savedRule.getId()
                )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                authorizationFor(USER_ID)
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message")
                        .value(
                                "Delete notification rule id "
                                        + savedRule.getId()
                                        + " successfully"
                        ));

        assertThat(ruleRepository.existsById(savedRule.getId()))
                .isFalse();
    }


    @Test
    void shouldRejectInvalidCreateRequest()
            throws Exception {

        // Arrange: threshold phải lớn hơn 0
        String requestBody = """
                {
                  "deviceUid": "%s",
                  "type": "POWER_THRESHOLD",
                  "threshold": 0,
                  "cooldownSeconds": 300,
                  "enabled": true
                }
                """.formatted(UUID.randomUUID());

        // Act + Assert
        mockMvc.perform(post("/api/v1/notification-rules")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                authorizationFor(USER_ID)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        assertThat(ruleRepository.count()).isZero();
    }

    @Test
    void shouldRejectRequestWithoutJwt()
            throws Exception {

        mockMvc.perform(get("/api/v1/notification-rules")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isUnauthorized());
    }

}
