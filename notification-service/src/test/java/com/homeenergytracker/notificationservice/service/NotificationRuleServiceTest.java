package com.homeenergytracker.notificationservice.service;

import com.homeenergytracker.notificationservice.dto.CreateNotificationRule;
import com.homeenergytracker.notificationservice.dto.PageResponse;
import com.homeenergytracker.notificationservice.dto.UpdateNotificationRule;
import com.homeenergytracker.notificationservice.entity.NotificationRule;
import com.homeenergytracker.notificationservice.entity.NotificationType;
import com.homeenergytracker.notificationservice.exception.NotificationRuleNotExist;
import com.homeenergytracker.notificationservice.mapper.NotificationRuleMapper;
import com.homeenergytracker.notificationservice.repository.NotificationRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRuleServiceTest {

    private static final Long RULE_ID = 10L;
    private static final Long USER_ID = 1L;
    private static final UUID DEVICE_UID =
            UUID.fromString("4d7e16e7-808c-4ba0-92c4-91fc1a470fd4");

    @Mock
    private NotificationRuleRepository repository;

    @Mock
    private NotificationRuleMapper mapper;

    private NotificationRuleService service;

    @BeforeEach
    void setUp() {
        service = new NotificationRuleService(repository, mapper);
    }

    @Test
    void shouldCreateRuleForAuthenticatedUser() {
        CreateNotificationRule request = createRequest();
        NotificationRule mappedRule = new NotificationRule();
        NotificationRule savedRule = rule();

        when(mapper.toEntity(request)).thenReturn(mappedRule);
        when(repository.save(mappedRule)).thenReturn(savedRule);

        NotificationRule result =
                service.createNotificationRule(request, USER_ID);

        assertSame(savedRule, result);
        assertEquals(USER_ID, mappedRule.getUserId());
        verify(mapper).toEntity(request);
        verify(repository).save(mappedRule);
    }

    @Test
    void shouldUpdateRuleOwnedByUser() {
        UpdateNotificationRule request = updateRequest();
        NotificationRule existingRule = rule();

        when(repository.findByIdAndUserId(RULE_ID, USER_ID))
                .thenReturn(Optional.of(existingRule));

        NotificationRule result = service.updateNotificationRule(
                request,
                RULE_ID,
                USER_ID
        );

        assertSame(existingRule, result);
        verify(mapper).updateEntity(request, existingRule);
    }

    @Test
    void shouldRejectUpdateWhenRuleIsNotOwnedByUser() {
        UpdateNotificationRule request = updateRequest();
        when(repository.findByIdAndUserId(RULE_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                NotificationRuleNotExist.class,
                () -> service.updateNotificationRule(
                        request,
                        RULE_ID,
                        USER_ID
                )
        );

        verifyNoInteractions(mapper);
    }

    @Test
    void shouldDeleteRuleOwnedByUser() {
        NotificationRule existingRule = rule();
        when(repository.findByIdAndUserId(RULE_ID, USER_ID))
                .thenReturn(Optional.of(existingRule));

        service.deleteNotificationRule(RULE_ID, USER_ID);

        verify(repository).delete(existingRule);
    }

    @Test
    void shouldRejectDeleteWhenRuleIsNotOwnedByUser() {
        when(repository.findByIdAndUserId(RULE_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                NotificationRuleNotExist.class,
                () -> service.deleteNotificationRule(RULE_ID, USER_ID)
        );

        verify(repository, never()).delete(
                org.mockito.ArgumentMatchers.any(NotificationRule.class)
        );
    }

    @Test
    void shouldReturnRuleOwnedByUser() {
        NotificationRule existingRule = rule();
        when(repository.findByIdAndUserId(RULE_ID, USER_ID))
                .thenReturn(Optional.of(existingRule));

        NotificationRule result =
                service.getNotificationRule(RULE_ID, USER_ID);

        assertSame(existingRule, result);
    }

    @Test
    void shouldRejectGetWhenRuleIsNotOwnedByUser() {
        when(repository.findByIdAndUserId(RULE_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                NotificationRuleNotExist.class,
                () -> service.getNotificationRule(RULE_ID, USER_ID)
        );
    }

    @Test
    void shouldNormalizeInvalidPageAndSize() {
        NotificationRule existingRule = rule();
        PageRequest expectedPageable = PageRequest.of(0, 10);
        when(repository.findAllByUserIdOrderByCreatedAtDesc(
                USER_ID,
                expectedPageable
        )).thenReturn(new PageImpl<>(
                List.of(existingRule),
                expectedPageable,
                1
        ));

        PageResponse<NotificationRule> result =
                service.getAllNotificationRules(USER_ID, -5, 0);

        assertEquals(List.of(existingRule), result.items());
        assertEquals(0, result.currentPage());
        assertEquals(10, result.pageSize());
        assertEquals(1, result.totalPage());
        verify(repository).findAllByUserIdOrderByCreatedAtDesc(
                USER_ID,
                expectedPageable
        );
    }

    @Test
    void shouldLimitPageSizeToMaximum() {
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);
        PageRequest returnedPageable = PageRequest.of(2, 20);
        when(repository.findAllByUserIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.<NotificationRule>of(),
                returnedPageable,
                0
        ));

        service.getAllNotificationRules(USER_ID, 2, 100);

        verify(repository).findAllByUserIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                pageableCaptor.capture()
        );
        assertEquals(2, pageableCaptor.getValue().getPageNumber());
        assertEquals(20, pageableCaptor.getValue().getPageSize());
    }

    private CreateNotificationRule createRequest() {
        return new CreateNotificationRule(
                DEVICE_UID,
                NotificationType.POWER_THRESHOLD,
                new BigDecimal("1000.000"),
                300L,
                true
        );
    }

    private UpdateNotificationRule updateRequest() {
        return new UpdateNotificationRule(
                NotificationType.POWER_THRESHOLD,
                new BigDecimal("1500.000"),
                600L,
                false
        );
    }

    private NotificationRule rule() {
        NotificationRule rule = new NotificationRule();
        rule.setId(RULE_ID);
        rule.setUserId(USER_ID);
        rule.setDeviceUid(DEVICE_UID);
        rule.setType(NotificationType.POWER_THRESHOLD);
        rule.setThreshold(new BigDecimal("1000.000"));
        rule.setCooldownSeconds(300L);
        rule.setEnabled(true);
        return rule;
    }
}
