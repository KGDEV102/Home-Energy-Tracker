package com.homeenergytracker.notificationservice.integration.client;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.notificationservice.client.UserServiceClient;
import com.homeenergytracker.notificationservice.dto.UserNotificationContactResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserServiceClientIntegrationTest {

    private static final String BASE_URL =
            "http://user-service.test";
    private static final Long USER_ID = 1L;

    private MockRestServiceServer server;
    private UserServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder()
                .baseUrl(BASE_URL);

        server = MockRestServiceServer
                .bindTo(restClientBuilder)
                .build();

        RestClient restClient = restClientBuilder.build();

        HttpServiceProxyFactory proxyFactory =
                HttpServiceProxyFactory.builderFor(
                        RestClientAdapter.create(restClient)
                ).build();

        client = proxyFactory.createClient(UserServiceClient.class);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void shouldCallUserServiceAndDeserializeNotificationContact() {
        server.expect(requestTo(
                        BASE_URL
                                + "/internal/v1/users/1/notification-contact"
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {
                          "success": true,
                          "data": {
                            "userId": 1,
                            "email": "user@example.com"
                          },
                          "message": "Operation Successful"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        SuccessResponse<UserNotificationContactResponse> response =
                client.getNotificationContact(USER_ID);

        assertTrue(response.success());
        assertEquals(USER_ID, response.data().userId());
        assertEquals("user@example.com", response.data().email());
        assertEquals("Operation Successful", response.message());
    }

    @Test
    void shouldPropagateNotFoundResponse() {
        server.expect(requestTo(
                        BASE_URL
                                + "/internal/v1/users/1/notification-contact"
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(
                HttpClientErrorException.NotFound.class,
                () -> client.getNotificationContact(USER_ID)
        );
    }

    @Test
    void shouldPropagateServerErrorResponse() {
        server.expect(requestTo(
                        BASE_URL
                                + "/internal/v1/users/1/notification-contact"
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(
                HttpServerErrorException.InternalServerError.class,
                () -> client.getNotificationContact(USER_ID)
        );
    }

    @Test
    void shouldRejectMalformedJsonResponse() {
        server.expect(requestTo(
                        BASE_URL
                                + "/internal/v1/users/1/notification-contact"
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"success\": true, \"data\":",
                        MediaType.APPLICATION_JSON
                ));

        assertThrows(
                RestClientException.class,
                () -> client.getNotificationContact(USER_ID)
        );
    }
}
