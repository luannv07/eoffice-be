package vn.edu.epu.eoffice_be.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ProfileCompletedFilterTest {

	final ProfileCompletedFilter filter = new ProfileCompletedFilter(new ObjectMapper().findAndRegisterModules());

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void shouldReturnForbiddenWhenProfileIsIncompleteForProtectedEndpoint() throws Exception {
		JwtUserPrincipal principal = new JwtUserPrincipal(10L, "alice", "USER", false, List.of());
		SecurityContextHolder.getContext().setAuthentication(
						UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of())
		);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/org-nodes");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		assertEquals(403, response.getStatus());
		assertTrue(response.getContentAsString().contains(ErrorCode.PROFILE_INCOMPLETE.getCode()));
		assertTrue(response.getContentAsString().contains(ErrorCode.PROFILE_INCOMPLETE.getMessage()));
		verify(chain, never()).doFilter(request, response);
	}

	@Test
	void shouldAllowAuthMeWhenProfileIsIncomplete() throws Exception {
		JwtUserPrincipal principal = new JwtUserPrincipal(10L, "alice", "USER", false, List.of());
		SecurityContextHolder.getContext().setAuthentication(
						UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of())
		);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
	}
}
