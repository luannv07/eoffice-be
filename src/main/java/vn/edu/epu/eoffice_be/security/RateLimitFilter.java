package vn.edu.epu.eoffice_be.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.config.RateLimitProperties;
import vn.edu.epu.eoffice_be.config.SecurityWhitelist;
import vn.edu.epu.eoffice_be.utils.AuthHttpUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RateLimitFilter extends OncePerRequestFilter {

	RateLimitProperties properties;
	ObjectMapper objectMapper;
	Cache<String, Bucket> buckets;

	public RateLimitFilter(RateLimitProperties properties,
	                       ObjectMapper objectMapper,
	                       @Qualifier("rateLimitCacheBuilder") Caffeine<Object, Object> cacheBuilder) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.buckets = cacheBuilder.build();
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !SecurityWhitelist.isRateLimited(requestPath(request));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
					throws ServletException, IOException {
		String clientKey = AuthHttpUtils.clientIp(request) + ":" + request.getMethod() + ":" + requestPath(request);
		if (!tryAcquire(clientKey)) {
			writeRateLimitedResponse(response);
			return;
		}
		filterChain.doFilter(request, response);
	}

	private boolean tryAcquire(String clientKey) {
		return buckets.get(clientKey,
										key -> new Bucket(getWindow(), getLimit()))
						.allow();
	}

	private Duration getWindow() {
		return Duration.ofSeconds(properties.getEndpointWindowSeconds());
	}

	private int getLimit() {
		return properties.getEndpointLimit();
	}

	private String requestPath(HttpServletRequest request) {
		String path = request.getServletPath();
		if (path == null || path.isBlank()) {
			path = request.getRequestURI();
			String contextPath = request.getContextPath();
			if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
				path = path.substring(contextPath.length());
			}
		}
		return path;
	}

	private void writeRateLimitedResponse(HttpServletResponse response) throws IOException {
		ErrorCode errorCode = ErrorCode.RATE_LIMITED;
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getWriter(), BaseResponse.error(errorCode.getMessage(), errorCode.getCode()));
	}

	private static final class Bucket {
		final Duration window;
		final int limit;
		Instant windowStart;
		AtomicInteger count;

		Bucket(Duration window, int limit) {
			this.window = window;
			this.limit = limit;
			this.windowStart = Instant.now();
			this.count = new AtomicInteger(0);
		}

		synchronized boolean allow() {
			Instant now = Instant.now();
			if (now.isBefore(windowStart.plus(window))) {
				return count.incrementAndGet() <= limit;
			}
			windowStart = now;
			count.set(1);
			return true;
		}
	}
}
