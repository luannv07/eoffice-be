package vn.edu.epu.eoffice_be.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    final LoginRateLimiter rateLimiter = mock(LoginRateLimiter.class);
    final RateLimitFilter filter = new RateLimitFilter(rateLimiter, new ObjectMapper().findAndRegisterModules());

    @Test
    void shouldReturnTooManyRequestsForRateLimitedEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/forgot-password");
        request.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(rateLimiter.tryAcquire("1.2.3.4:POST:/auth/forgot-password")).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString().contains(ErrorCode.RATE_LIMITED.getCode()));
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void shouldSkipEndpointNotListedForRateLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(rateLimiter, never()).tryAcquire(org.mockito.ArgumentMatchers.anyString());
        verify(chain).doFilter(request, response);
    }
}
