package vn.edu.epu.eoffice_be.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

	private final CorrelationIdFilter filter = new CorrelationIdFilter();

	@AfterEach
	void clearMdc() {
		MDC.remove(CorrelationIdFilter.CORRELATION_ID);
	}

	@Test
	void doFilterShouldReuseIncomingCorrelationIdWhenHeaderPresent() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Correlation-Id", "incoming-123");
		MockHttpServletResponse response = new MockHttpServletResponse();

		final String[] captured = new String[1];
		FilterChain chain = (req, res) -> captured[0] = MDC.get(CorrelationIdFilter.CORRELATION_ID);

		filter.doFilter(request, response, chain);

		assertEquals("incoming-123", captured[0]);
		assertEquals("incoming-123", response.getHeader("X-Correlation-Id"));
	}

	@Test
	void doFilterShouldGenerateCorrelationIdWhenHeaderMissing() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		final String[] captured = new String[1];
		FilterChain chain = (req, res) -> captured[0] = MDC.get(CorrelationIdFilter.CORRELATION_ID);

		filter.doFilter(request, response, chain);

		assertNotNull(captured[0]);
		assertTrue(captured[0].length() > 0);
		assertEquals(captured[0], response.getHeader("X-Correlation-Id"));
		// MDC must be cleared after the request completes
		assertEquals(null, MDC.get(CorrelationIdFilter.CORRELATION_ID));
	}

	@Test
	void doFilterShouldGenerateCorrelationIdWhenHeaderBlank() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Correlation-Id", "   ");
		MockHttpServletResponse response = new MockHttpServletResponse();

		final String[] captured = new String[1];
		FilterChain chain = (req, res) -> captured[0] = MDC.get(CorrelationIdFilter.CORRELATION_ID);

		filter.doFilter(request, response, chain);

		assertNotNull(captured[0]);
		assertTrue(captured[0].length() > 0);
	}

	@Test
	void doFilterShouldClearMdcEvenWhenChainThrows() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Correlation-Id", "err-1");
		MockHttpServletResponse response = new MockHttpServletResponse();

		FilterChain failingChain = (req, res) -> {
			throw new IOException("boom");
		};

		try {
			filter.doFilter(request, response, failingChain);
		} catch (IOException ignored) {
			// expected
		}
		assertEquals(null, MDC.get(CorrelationIdFilter.CORRELATION_ID));
	}
}
