package com.homeenergytracker.notificationservice.integration.api.flow;

import com.homeenergytracker.common.event.EnergyUsageEvent;
import com.homeenergytracker.common.event.ReadingSource;
import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.notificationservice.client.UserServiceClient;
import com.homeenergytracker.notificationservice.dto.UserNotificationContactResponse;
import com.homeenergytracker.notificationservice.entity.DeliveryStatus;
import com.homeenergytracker.notificationservice.entity.Notification;
import com.homeenergytracker.notificationservice.entity.NotificationRule;
import com.homeenergytracker.notificationservice.entity.NotificationType;
import com.homeenergytracker.notificationservice.repository.NotificationRepository;
import com.homeenergytracker.notificationservice.repository.NotificationRuleRepository;
import com.homeenergytracker.notificationservice.service.NotificationEvaluationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "notification.kafka.energy-usage-topic=energy-usage.notification-flow-test",
        "spring.kafka.consumer.group-id=notification-flow-test",
        "internal.user-service-token=test-internal-token",
        "notification.delivery.poll-delay-ms=100",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer"
})
@EmbeddedKafka(
        partitions = 1,
        topics = "energy-usage.notification-flow-test",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
public class NotificationFlowIntegrationTest {


    private static final String TOPIC =
            "energy-usage.notification-flow-test";

    private static final Long USER_ID = 1L;

    private static final String EMAIL =
            "user@example.com";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer("postgres:17-alpine");


    @Container
    static final GenericContainer<?> MAILPIT =
            new GenericContainer<>(
                    DockerImageName.parse("axllent/mailpit:v1.27.4")
            )
                    .withExposedPorts(1025, 8025)
                    .waitingFor(Wait.forListeningPort());
    @DynamicPropertySource
    static void configureMailpit(
            DynamicPropertyRegistry registry
    ) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add(
                "spring.mail.port",
                () -> MAILPIT.getMappedPort(1025)
        );
        registry.add("spring.mail.properties.mail.smtp.auth", () -> false);
        registry.add(
                "spring.mail.properties.mail.smtp.starttls.enable",
                () -> false
        );
    }

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private NotificationRuleRepository ruleRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    /*
     * Flow Kafka không dùng JWT, nhưng SecurityConfig cần JwtDecoder
     * để application context khởi động được.
     */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    /*
     * Tạm mock boundary user-service.
     * Test HTTP client riêng có thể làm sau bằng WireMock.
     */
    @MockitoBean
    private UserServiceClient userServiceClient;

    @MockitoSpyBean
    private NotificationEvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        ruleRepository.deleteAll();

        when(userServiceClient.getNotificationContact(USER_ID, "test-internal-token"))
                .thenReturn(
                        SuccessResponse.of(
                                new UserNotificationContactResponse(
                                        USER_ID,
                                        EMAIL
                                )
                        )
                );
    }
    private NotificationRule createPowerThresholdRule(
            UUID deviceUid,
            BigDecimal threshold
    ) {
        NotificationRule rule = new NotificationRule();

        rule.setUserId(USER_ID);
        rule.setDeviceUid(deviceUid);
        rule.setType(NotificationType.POWER_THRESHOLD);
        rule.setThreshold(threshold);
        rule.setCooldownSeconds(300L);
        rule.setEnabled(true);

        return ruleRepository.saveAndFlush(rule);
    }

    private EnergyUsageEvent eventWithPower(
            UUID eventId,
            UUID deviceUid,
            BigDecimal averagePowerW
    ) {
        Instant measuredAt = Instant.now().minusSeconds(1);

        return new EnergyUsageEvent(
                eventId,
                1,
                deviceUid,
                USER_ID,
                "AIR_CONDITIONER",
                averagePowerW,
                new BigDecimal("1.670"),
                measuredAt,
                measuredAt.plusSeconds(1),
                ReadingSource.SIMULATOR
        );
    }

    private void send(EnergyUsageEvent event) throws Exception {
        kafkaTemplate.send(
                TOPIC,
                event.deviceUid().toString(),
                event
        ).get(10, TimeUnit.SECONDS);
    }

    private void assertEventWasEvaluatedWithoutNotification(
            EnergyUsageEvent event
    ) {
        verify(evaluationService, timeout(10_000))
                .evaluate(event);

        await()
                .pollDelay(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() ->
                        assertThat(notificationRepository.findAll())
                                .isEmpty()
                );

        verify(userServiceClient, never())
                .getNotificationContact(USER_ID, "test-internal-token");
    }
    @Test
    void shouldCreateNotificationAndSendEmailWhenThresholdExceeded()
            throws Exception {

        // Arrange: tạo rule thật trong PostgreSQL
        UUID deviceUid = UUID.randomUUID();

        NotificationRule rule = createPowerThresholdRule(
                deviceUid,
                new BigDecimal("1000.000")
        );

        Instant measuredAt = Instant.now().minusSeconds(1);

        EnergyUsageEvent event = new EnergyUsageEvent(
                UUID.randomUUID(),
                1,
                deviceUid,
                USER_ID,
                "AIR_CONDITIONER",
                new BigDecimal("1200.000"),
                new BigDecimal("1.670"),
                measuredAt,
                measuredAt.plusSeconds(1),
                com.homeenergytracker.common.event.ReadingSource.SIMULATOR
        );

        // Act: gửi event vào Embedded Kafka
        kafkaTemplate.send(
                TOPIC,
                deviceUid.toString(),
                event
        ).get(10, TimeUnit.SECONDS);

        /*
         * Kafka listener và gửi email chạy bất đồng bộ,
         * vì vậy chờ tới khi notification chuyển thành SENT.
         */
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> {
                    List<Notification> notifications =
                            notificationRepository.findAll();

                    assertThat(notifications)
                            .singleElement()
                            .satisfies(notification -> {
                                assertThat(notification.getEventId())
                                        .isEqualTo(event.eventId());

                                assertThat(notification.getRuleId())
                                        .isEqualTo(rule.getId());

                                assertThat(notification.getUserId())
                                        .isEqualTo(USER_ID);

                                assertThat(notification.getDeviceUid())
                                        .isEqualTo(deviceUid);

                                assertThat(notification.getTriggerValue())
                                        .isEqualByComparingTo("1200.000");

                                assertThat(notification.getThresholdValue())
                                        .isEqualByComparingTo("1000.000");

                                assertThat(notification.getRecipientEmail())
                                        .isEqualTo(EMAIL);

                                assertThat(notification.getDeliveryStatus())
                                        .isEqualTo(DeliveryStatus.SENT);

                                assertThat(notification.getDeliveryAttempts())
                                        .isEqualTo(1);

                                assertThat(notification.getSentAt())
                                        .isNotNull();

                                assertThat(
                                        notification.getLastDeliveryError()
                                ).isNull();
                            });

                    NotificationRule updatedRule =
                            ruleRepository.findById(rule.getId())
                                    .orElseThrow();

                    assertThat(updatedRule.getLastTriggeredAt())
                            .isNotNull();
                });
    }

    @Test
    void shouldNotCreateNotificationWhenThresholdNotExceeded()
            throws Exception {
        UUID deviceUid = UUID.randomUUID();
        createPowerThresholdRule(
                deviceUid,
                new BigDecimal("1000.000")
        );

        EnergyUsageEvent event = eventWithPower(
                UUID.randomUUID(),
                deviceUid,
                new BigDecimal("900.000")
        );

        send(event);

        assertEventWasEvaluatedWithoutNotification(event);
    }

    @Test
    void shouldNotCreateNotificationDuringCooldown()
            throws Exception {
        UUID deviceUid = UUID.randomUUID();
        NotificationRule rule = createPowerThresholdRule(
                deviceUid,
                new BigDecimal("1000.000")
        );
        Instant previousTriggeredAt = Instant.now()
                .truncatedTo(ChronoUnit.MICROS);
        rule.setLastTriggeredAt(previousTriggeredAt);
        ruleRepository.saveAndFlush(rule);

        EnergyUsageEvent event = eventWithPower(
                UUID.randomUUID(),
                deviceUid,
                new BigDecimal("1200.000")
        );

        send(event);

        assertEventWasEvaluatedWithoutNotification(event);

        NotificationRule unchangedRule = ruleRepository
                .findById(rule.getId())
                .orElseThrow();
        assertThat(unchangedRule.getLastTriggeredAt())
                .isEqualTo(previousTriggeredAt);
    }

    @Test
    void shouldNotCreateDuplicateNotificationForSameEventAndRule()
            throws Exception {
        UUID deviceUid = UUID.randomUUID();
        NotificationRule rule = createPowerThresholdRule(
                deviceUid,
                new BigDecimal("1000.000")
        );
        EnergyUsageEvent event = eventWithPower(
                UUID.randomUUID(),
                deviceUid,
                new BigDecimal("1200.000")
        );

        send(event);

        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertThat(notificationRepository.findAll())
                                .singleElement()
                                .satisfies(notification ->
                                        assertThat(
                                                notification.getDeliveryStatus()
                                        ).isEqualTo(DeliveryStatus.SENT)
                                )
                );

        NotificationRule updatedRule = ruleRepository
                .findById(rule.getId())
                .orElseThrow();
        updatedRule.setCooldownSeconds(0L);
        ruleRepository.saveAndFlush(updatedRule);

        send(event);

        verify(evaluationService, timeout(10_000).times(2))
                .evaluate(event);

        await()
                .pollDelay(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() ->
                        assertThat(notificationRepository.findAll())
                                .singleElement()
                                .satisfies(notification -> {
                                    assertThat(notification.getEventId())
                                            .isEqualTo(event.eventId());
                                    assertThat(
                                            notification.getDeliveryAttempts()
                                    ).isEqualTo(1);
                                })
                );
    }

    @Test
    void shouldMarkNotificationAsFailedWhenContactCannotBeLoaded()
            throws Exception {
        UUID deviceUid = UUID.randomUUID();
        NotificationRule rule = createPowerThresholdRule(
                deviceUid,
                new BigDecimal("1000.000")
        );
        when(userServiceClient.getNotificationContact(USER_ID, "test-internal-token"))
                .thenReturn(null);

        EnergyUsageEvent event = eventWithPower(
                UUID.randomUUID(),
                deviceUid,
                new BigDecimal("1200.000")
        );

        send(event);

        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() ->
                        assertThat(notificationRepository.findAll())
                                .singleElement()
                                .satisfies(notification -> {
                                    assertThat(notification.getEventId())
                                            .isEqualTo(event.eventId());
                                    assertThat(notification.getRuleId())
                                            .isEqualTo(rule.getId());
                                    assertThat(
                                            notification.getDeliveryStatus()
                                    ).isEqualTo(DeliveryStatus.FAILED);
                                    assertThat(
                                            notification.getDeliveryAttempts()
                                    ).isEqualTo(1);
                                    assertThat(
                                            notification.getRecipientEmail()
                                    ).isNull();
                                    assertThat(notification.getSentAt())
                                            .isNull();
                                    assertThat(
                                            notification.getLastDeliveryError()
                                    ).contains(
                                            "Could not load notification contact"
                                    );
                                })
                );
    }

}
