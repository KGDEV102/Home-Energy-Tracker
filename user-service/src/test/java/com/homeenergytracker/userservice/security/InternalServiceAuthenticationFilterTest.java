package com.homeenergytracker.userservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InternalServiceAuthenticationFilterTest {
    private static final String TOKEN = "test-internal-token";

    @Test
    void acceptsInternalRequestWithMatchingToken() throws Exception {
        InternalServiceAuthenticationFilter filter =
                new InternalServiceAuthenticationFilter(TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/internal/v1/users/1/notification-contact"
        );
        request.addHeader("X-Internal-Token", TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger calls = new AtomicInteger();

        try {
            filter.doFilter(request, response, (req, res) -> {
                calls.incrementAndGet();
                assertThat(SecurityContextHolder.getContext()
                        .getAuthentication().getPrincipal())
                        .isEqualTo("notification-service");
            });
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(calls.get()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsInternalRequestWhenTokenIsMissingOrWrong() throws Exception {
        InternalServiceAuthenticationFilter filter =
                new InternalServiceAuthenticationFilter(TOKEN);

        for (String suppliedToken : new String[]{null, "wrong-token"}) {
            MockHttpServletRequest request = new MockHttpServletRequest(
                    "GET", "/internal/v1/users/1/notification-contact"
            );
            if (suppliedToken != null) {
                request.addHeader("X-Internal-Token", suppliedToken);
            }
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> {
                throw new AssertionError("Request must not reach the controller");
            });

            assertThat(response.getStatus()).isEqualTo(401);
        }
    }
}
