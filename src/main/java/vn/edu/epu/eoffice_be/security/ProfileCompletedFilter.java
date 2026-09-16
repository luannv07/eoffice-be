package vn.edu.epu.eoffice_be.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.config.SecurityWhitelist;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProfileCompletedFilter extends OncePerRequestFilter {

	ObjectMapper objectMapper;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = requestPath(request);
		return SecurityWhitelist.isProfileFilterExempt(path, request.getMethod());
	}

	private boolean matchesPathPrefix(String path, String prefix) {
        if (path == null || prefix == null || prefix.isBlank()) {
            return false;
        }
        if ("/".equals(prefix)) {
            return path.startsWith("/");
        }
        String normalizedPrefix = prefix.endsWith("/") 
                ? prefix.substring(0, prefix.length() - 1) 
                : prefix;
        return path.equals(normalizedPrefix) || path.startsWith(normalizedPrefix + "/");
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

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
					throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null
						&& authentication.isAuthenticated()
						&& authentication.getPrincipal() instanceof JwtUserPrincipal principal
						&& !principal.profileCompleted()) {
			writeProfileIncompleteResponse(response);
			return;
		}

		filterChain.doFilter(request, response);
	}

	private void writeProfileIncompleteResponse(HttpServletResponse response) throws IOException {
		ErrorCode errorCode = ErrorCode.PROFILE_INCOMPLETE;
		response.setStatus(HttpStatus.FORBIDDEN.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getWriter(), BaseResponse.error(errorCode.getMessage(), errorCode.getCode()));
	}
}
