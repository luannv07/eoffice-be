package vn.edu.epu.eoffice_be.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.config.RateLimitProperties;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    final RateLimitProperties properties = rateLimitProperties();
    final RateLimitFilter filter = new RateLimitFilter(
            properties,
            new ObjectMapper().findAndRegisterModules(),
            Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(properties.getEndpointWindowSeconds())));

    @Test
    void shouldReturnTooManyRequestsForRateLimitedEndpoint() throws Exception {
        MockHttpServletRequest firstRequest = new MockHttpServletRequest("POST", "/auth/forgot-password");
        firstRequest.setRemoteAddr("1.2.3.4");
        MockHttpServletRequest secondRequest = new MockHttpServletRequest("POST", "/auth/forgot-password");
        secondRequest.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        FilterChain firstChain = mock(FilterChain.class);
        FilterChain secondChain = mock(FilterChain.class);

        filter.doFilter(firstRequest, firstResponse, firstChain);
        filter.doFilter(secondRequest, secondResponse, secondChain);

        verify(firstChain).doFilter(firstRequest, firstResponse);
        assertEquals(429, secondResponse.getStatus());
        assertTrue(secondResponse.getContentAsString().contains(ErrorCode.RATE_LIMITED.getCode()));
        verify(secondChain, never()).doFilter(secondRequest, secondResponse);
    }

    @Test
    void shouldSkipEndpointNotListedForRateLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    private RateLimitProperties rateLimitProperties() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEndpointLimit(1);
        properties.setEndpointWindowSeconds(300);
        return properties;
    }
}
