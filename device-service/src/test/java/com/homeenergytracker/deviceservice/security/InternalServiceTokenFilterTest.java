package com.homeenergytracker.deviceservice.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class InternalServiceTokenFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesMatchingServiceToken() throws Exception {
        var filter = new InternalServiceTokenFilter("expected-token");
        var request = new MockHttpServletRequest("GET", "/internal/v1/devices/simulation-targets");
        request.addHeader(InternalServiceTokenFilter.HEADER_NAME, "expected-token");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_INTERNAL");
    }

    @Test
    void rejectsMissingServiceToken() throws Exception {
        var filter = new InternalServiceTokenFilter("expected-token");
        var request = new MockHttpServletRequest("GET", "/internal/v1/devices/simulation-targets");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void rejectsWrongServiceToken() throws Exception {
        var filter = new InternalServiceTokenFilter("expected-token");
        var request = new MockHttpServletRequest("GET", "/internal/v1/devices/simulation-targets");
        request.addHeader(InternalServiceTokenFilter.HEADER_NAME, "wrong-token");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }
}
