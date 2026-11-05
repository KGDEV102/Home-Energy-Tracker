package com.homeenergytracker.usageservice.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class InternalServiceTokenFilterTest {

    private static final String TOKEN = "test-internal-token";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesMatchingToken() throws Exception {
        var filter = new InternalServiceTokenFilter(TOKEN);
        var request = new MockHttpServletRequest(
                "GET",
                "/internal/v1/usages/users/42/summary"
        );
        request.addHeader(InternalServiceTokenFilter.HEADER_NAME, TOKEN);
        var response = new MockHttpServletResponse();
        var called = new AtomicBoolean();
        var principal = new AtomicReference<Object>();
        var authorities = new AtomicReference<Collection<? extends GrantedAuthority>>();
        jakarta.servlet.FilterChain chain = (req, res) -> {
            called.set(true);
            var authentication = SecurityContextHolder.getContext()
                    .getAuthentication();
            principal.set(authentication.getPrincipal());
            authorities.set(authentication.getAuthorities());
        };

        filter.doFilter(request, response, chain);

        assertThat(called).isTrue();
        assertThat(principal.get()).isEqualTo("insight-service");
        assertThat(authorities.get())
                .extracting("authority")
                .containsExactly("ROLE_INTERNAL_SERVICE");
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();
    }

    @Test
    void rejectsMissingToken() throws Exception {
        assertRejected(null);
    }

    @Test
    void rejectsWrongToken() throws Exception {
        assertRejected("wrong-token");
    }

    private void assertRejected(String suppliedToken) throws Exception {
        var filter = new InternalServiceTokenFilter(TOKEN);
        var request = new MockHttpServletRequest(
                "GET",
                "/internal/v1/usages/users/42/summary"
        );
        if (suppliedToken != null) {
            request.addHeader(
                    InternalServiceTokenFilter.HEADER_NAME,
                    suppliedToken
            );
        }
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }
}
