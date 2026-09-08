package vn.edu.epu.eoffice_be.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.config.JwtProperties;

import java.time.Duration;
import java.util.List;

public final class AuthHttpUtils {

	private AuthHttpUtils() {
	}

	public static String clientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

	public static String refreshTokenFrom(HttpServletRequest request, String cookieName) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (cookieName.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	public static void setRefreshTokenCookie(HttpServletResponse response, String value, JwtProperties jwtProperties) {
		ResponseCookie cookie = ResponseCookie.from(jwtProperties.getCookieName(), value)
						.httpOnly(true)
						.secure(jwtProperties.isCookieSecure())
						.sameSite(jwtProperties.getCookieSameSite())
						.path(jwtProperties.getCookiePath())
						.maxAge(jwtProperties.getRefreshTokenTtl())
						.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	public static void clearRefreshTokenCookie(HttpServletResponse response, JwtProperties jwtProperties) {
		ResponseCookie cookie = ResponseCookie.from(jwtProperties.getCookieName(), "")
						.httpOnly(true)
						.secure(jwtProperties.isCookieSecure())
						.sameSite(jwtProperties.getCookieSameSite())
						.path(jwtProperties.getCookiePath())
						.maxAge(Duration.ZERO)
						.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	public static void assertAllowedOrigin(HttpServletRequest request, List<String> allowedOrigins) {
		String origin = request.getHeader("Origin");
		if (origin == null || origin.isBlank()) {
			return;
		}
		if (allowedOrigins.contains(origin) || isSameOrigin(request, origin)) {
			return;
		}
		throw new BusinessException(ErrorCode.FORBIDDEN);
	}

	private static boolean isSameOrigin(HttpServletRequest request, String origin) {
		String expected = request.getScheme() + "://" + request.getServerName();
		if (request.getServerPort() != 80 && request.getServerPort() != 443) {
			expected += ":" + request.getServerPort();
		}
		return origin.equals(expected);
	}
}
