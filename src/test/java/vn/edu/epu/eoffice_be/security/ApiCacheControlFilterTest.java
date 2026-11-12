package vn.edu.epu.eoffice_be.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ApiCacheControlFilterTest {

	private final ApiCacheControlFilter filter = new ApiCacheControlFilter();

	@Test
	void shouldAddNoStoreHeaderForApiRequests() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/documents");
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicInteger chainCallCount = new AtomicInteger(0);

		FilterChain chain = new FilterChain() {
			@Override
			public void doFilter(ServletRequest req, ServletResponse res) {
				chainCallCount.incrementAndGet();
			}
		};

		filter.doFilter(request, response, chain);

		assertEquals("no-store, max-age=0, private", response.getHeader("Cache-Control"));
		assertEquals("no-cache", response.getHeader("Pragma"));
		assertEquals(1, chainCallCount.get(), "FilterChain.doFilter should be called exactly once");
	}

	@Test
	void shouldNotFilterSwaggerUiAndStaticAssets() {
		MockHttpServletRequest swaggerRequest = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
		assertTrue(filter.shouldNotFilter(swaggerRequest));

		MockHttpServletRequest apiDocsRequest = new MockHttpServletRequest("GET", "/v3/api-docs/swagger-config");
		assertTrue(filter.shouldNotFilter(apiDocsRequest));

		MockHttpServletRequest cssRequest = new MockHttpServletRequest("GET", "/static/app.css");
		assertTrue(filter.shouldNotFilter(cssRequest));

		MockHttpServletRequest jsRequest = new MockHttpServletRequest("GET", "/static/bundle.js");
		assertTrue(filter.shouldNotFilter(jsRequest));

		MockHttpServletRequest apiRequest = new MockHttpServletRequest("GET", "/api/v1/users");
		assertFalse(filter.shouldNotFilter(apiRequest));
	}
}
