package vn.edu.epu.eoffice_be.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ensures sensitive API responses contain proper Cache-Control headers (no-store, max-age=0, private)
 * to prevent caching of personal data, authentication tokens, internal documents, and business records.
 * Static and documentation assets (Swagger UI, OpenAPI docs, favicon) are excluded to avoid breaking caching for non-sensitive resources.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class ApiCacheControlFilter extends OncePerRequestFilter {

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String uri = request.getRequestURI();
		if (uri == null) {
			return false;
		}
		// Only exclude well-known documentation and static asset path prefixes.
		// Business API endpoints (even those returning .html/.png files) remain protected.
		return uri.startsWith("/swagger-ui/")
				|| uri.startsWith("/v3/api-docs")
				|| uri.startsWith("/webjars/")
				|| uri.equals("/favicon.ico");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		response.setHeader("Cache-Control", "no-store, max-age=0, private");
		response.setHeader("Pragma", "no-cache");
		filterChain.doFilter(request, response);
	}
}

