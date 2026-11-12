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
	void shouldNotFilterSwaggerUiAndDocAssets() {
		assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/swagger-ui/index.html")));
		assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/swagger-ui/swagger-ui.css")));
		assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/v3/api-docs")));
		assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/v3/api-docs/swagger-config")));
		assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/webjars/swagger-ui/bundle.js")));
		assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/favicon.ico")));
	}

	@Test
	void shouldFilterBusinessApiEvenWithFileExtensions() {
		// Business API endpoints returning files must still get Cache-Control headers
		assertFalse(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/documents/export.html")));
		assertFalse(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/documents/123/preview.png")));
		assertFalse(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/reports/annual.css")));
		assertFalse(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/users")));
	}
}
