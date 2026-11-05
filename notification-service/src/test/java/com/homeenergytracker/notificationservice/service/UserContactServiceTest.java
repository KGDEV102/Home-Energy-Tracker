package com.homeenergytracker.notificationservice.service;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.notificationservice.client.UserServiceClient;
import com.homeenergytracker.notificationservice.dto.UserNotificationContactResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserContactServiceTest {
    private static final Long USER_ID = 1L;
    private static final String INTERNAL_TOKEN = "test-internal-token";

    @Mock
    private UserServiceClient userServiceClient;

    private UserContactService service;

    @BeforeEach
    void setUp() {
        service = new UserContactService(userServiceClient, INTERNAL_TOKEN);
    }

    @Test
    void shouldRejectBlankInternalToken() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new UserContactService(userServiceClient, " ")
        );

        assertEquals(
                "internal.user-service-token must not be blank",
                exception.getMessage()
        );
    }

    @Test
    void shouldReturnContactWhenResponseIsValid() {
        UserNotificationContactResponse contact =
                new UserNotificationContactResponse(
                        USER_ID,
                        "user@example.com"
                );

        when(userServiceClient.getNotificationContact(USER_ID, INTERNAL_TOKEN))
                .thenReturn(SuccessResponse.of(contact));

        UserNotificationContactResponse result =
                service.getNotificationContact(USER_ID);

        assertSame(contact, result);
        verify(userServiceClient).getNotificationContact(USER_ID, INTERNAL_TOKEN);
    }

    @Test
    void shouldRejectNullResponse() {
        when(userServiceClient.getNotificationContact(USER_ID, INTERNAL_TOKEN))
                .thenReturn(null);

        assertInvalidContactResponse();
    }

    @Test
    void shouldRejectUnsuccessfulResponse() {
        UserNotificationContactResponse contact =
                new UserNotificationContactResponse(
                        USER_ID,
                        "user@example.com"
                );

        when(userServiceClient.getNotificationContact(USER_ID, INTERNAL_TOKEN))
                .thenReturn(new SuccessResponse<>(
                        false,
                        contact,
                        "Request failed"
                ));

        assertInvalidContactResponse();
    }

    @Test
    void shouldRejectResponseWithoutData() {
        when(userServiceClient.getNotificationContact(USER_ID, INTERNAL_TOKEN))
                .thenReturn(new SuccessResponse<>(
                        true,
                        null,
                        "Operation Successful"
                ));

        assertInvalidContactResponse();
    }

    @Test
    void shouldRejectNullEmail() {
        assertInvalidEmail(null);
    }

    @Test
    void shouldRejectEmptyEmail() {
        assertInvalidEmail("");
    }

    @Test
    void shouldRejectBlankEmail() {
        assertInvalidEmail("   ");
    }

    private void assertInvalidEmail(String email) {
        UserNotificationContactResponse contact =
                new UserNotificationContactResponse(USER_ID, email);

        when(userServiceClient.getNotificationContact(USER_ID, INTERNAL_TOKEN))
                .thenReturn(SuccessResponse.of(contact));

        assertInvalidContactResponse();
    }

    @Test
    void shouldRejectContactForDifferentUser() {
        UserNotificationContactResponse contact =
                new UserNotificationContactResponse(
                        2L,
                        "other@example.com"
                );

        when(userServiceClient.getNotificationContact(USER_ID, INTERNAL_TOKEN))
                .thenReturn(SuccessResponse.of(contact));

        assertInvalidContactResponse();
    }

    private void assertInvalidContactResponse() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.getNotificationContact(USER_ID)
        );

        assertEquals(
                "Could not load notification contact for user 1",
                exception.getMessage()
        );
        verify(userServiceClient).getNotificationContact(USER_ID, INTERNAL_TOKEN);
    }
}
