package vn.edu.epu.eoffice_be.config;

import org.springframework.http.HttpMethod;

/**
 * Centralized whitelist constants and helpers for security filters.
 */
public final class SecurityWhitelist {

	public static final String[] SWAGGER_WHITELIST = {
					"/v3/api-docs/**",
					"/swagger-ui/**",
					"/swagger-ui.html"
	};

	public static final String[] HEALTH_WHITELIST = {
					"/public/health",
					"/actuator/health",
					"/actuator/health/**"
	};

	public static final String[] PUBLIC_ENDPOINTS = {
					"/public/**",
					"/auth/login",
					"/auth/register",
					"/auth/refresh",
					"/auth/logout",
					"/auth/forgot-password",
					"/auth/reset-password"
	};

	public static final String[] REFRESH_TOKEN_ONLY_ENDPOINTS = {
					"/auth/logout",
					"/auth/refresh"
	};

	public static final String[] RATE_LIMITED_ENDPOINTS = {
					"/auth/login",
					"/auth/register",
					"/auth/refresh",
					"/auth/forgot-password",
					"/auth/reset-password"
	};

	private SecurityWhitelist() {
	}

	public static boolean isProfileFilterExempt(String path, String httpMethod) {
		if (HttpMethod.OPTIONS.matches(httpMethod)) return true;

		if (path == null || path.isBlank()) return false;

		// swagger / api docs / health
		if (path.startsWith("/public/")
						|| path.startsWith("/actuator/health")
						|| path.startsWith("/v3/api-docs")
						|| path.startsWith("/swagger-ui")
						|| path.equals("/swagger-ui.html")) {
			return true;
		}

		// auth endpoints
		if (path.equals("/auth/login")
						|| path.equals("/auth/register")
						|| path.equals("/auth/refresh")
						|| path.equals("/auth/logout")
						|| path.equals("/auth/forgot-password")
						|| path.equals("/auth/reset-password")
						|| path.equals("/auth/me")) {
			return true;
		}

		// dictionary items GET
		if (HttpMethod.GET.matches(httpMethod) && (path.equals("/dictionary-items") || path.startsWith("/dictionary-items/"))) {
			return true;
		}

		// profile and user-info read endpoints
		if (path.equals("/profile") || path.startsWith("/profile/")
						|| path.equals("/user-info") || path.startsWith("/user-info/")
						|| path.equals("/user-infos") || path.startsWith("/user-infos/")) {
			return true;
		}

		return false;
	}

	public static boolean isRateLimited(String path) {
		if (path == null || path.isBlank()) {
			return false;
		}
		for (String endpoint : RATE_LIMITED_ENDPOINTS) {
			if (path.equals(endpoint)) {
				return true;
			}
		}
		return false;
	}
}
