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
		// Allow caching for Swagger UI and static documentation assets
		return uri.contains("/swagger-ui")
				|| uri.contains("/v3/api-docs")
				|| uri.endsWith(".html")
				|| uri.endsWith(".css")
				|| uri.endsWith(".js")
				|| uri.endsWith(".ico")
				|| uri.endsWith(".png")
				|| uri.endsWith(".svg");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		response.setHeader("Cache-Control", "no-store, max-age=0, private");
		response.setHeader("Pragma", "no-cache");
		filterChain.doFilter(request, response);
	}
}

