package com.homeenergytracker.notificationservice.consumer;

import com.homeenergytracker.common.event.EnergyUsageEvent;
import com.homeenergytracker.notificationservice.service.NotificationEvaluationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EnergyUsageConsumerTest {

    @Mock
    private NotificationEvaluationService notificationEvaluationService;

    @InjectMocks
    private EnergyUsageConsumer consumer;

    @Mock
    private EnergyUsageEvent event;

    @Test
    void shouldDelegateEventToEvaluationService() {
        consumer.consume(event);

        verify(notificationEvaluationService).evaluate(event);
    }

    @Test
    void shouldPropagateEvaluationExceptionForKafkaErrorHandling() {
        RuntimeException expected = new RuntimeException("evaluation failed");
        doThrow(expected)
                .when(notificationEvaluationService)
                .evaluate(event);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> consumer.consume(event)
        );

        assertSame(expected, actual);
        verify(notificationEvaluationService).evaluate(event);
    }
}
