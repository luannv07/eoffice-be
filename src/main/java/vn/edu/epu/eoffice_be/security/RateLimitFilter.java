package vn.edu.epu.eoffice_be.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.config.SecurityWhitelist;
import vn.edu.epu.eoffice_be.utils.AuthHttpUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RateLimitFilter extends OncePerRequestFilter {

    LoginRateLimiter rateLimiter;
    ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !SecurityWhitelist.isRateLimited(requestPath(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientKey = AuthHttpUtils.clientIp(request) + ":" + request.getMethod() + ":" + requestPath(request);
        if (!rateLimiter.tryAcquire(clientKey)) {
            writeRateLimitedResponse(response);
            return;
        }
        filterChain.doFilter(request, response);
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
}
