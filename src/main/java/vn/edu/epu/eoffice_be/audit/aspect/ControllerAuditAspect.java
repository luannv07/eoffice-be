package vn.edu.epu.eoffice_be.audit.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.edu.epu.eoffice_be.audit.entity.UserAuditLog;
import vn.edu.epu.eoffice_be.audit.service.AuditLogAsyncService;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ControllerAuditAspect {

	private final AuditLogAsyncService auditLogAsyncService;
	private final ObjectMapper objectMapper;

	@Around("execution(public * vn.edu.epu.eoffice_be.controller..*(..))")
	public Object auditController(ProceedingJoinPoint joinPoint) throws Throwable {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		Method method = signature.getMethod();
		Class<?> targetClass = joinPoint.getTarget().getClass();

		boolean classAnnotated = AnnotatedElementUtils.hasAnnotation(targetClass, Auditable.class);
		boolean methodAnnotated = AnnotatedElementUtils.hasAnnotation(method, Auditable.class);
		if (!classAnnotated && !methodAnnotated) {
			return joinPoint.proceed();
		}

		long start = System.currentTimeMillis();
		Throwable throwable = null;
		try {
			return joinPoint.proceed();
		} catch (Throwable ex) {
			throwable = ex;
			throw ex;
		} finally {
			saveAuditLog(joinPoint, method, start, throwable);
		}
	}

	private void saveAuditLog(ProceedingJoinPoint joinPoint, Method method, long start, Throwable throwable) {
		RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
		HttpServletRequest request = requestAttributes instanceof ServletRequestAttributes servletRequestAttributes
						? servletRequestAttributes.getRequest()
						: null;

		String username = resolveUsername();
		String params = serializeParams(joinPoint.getArgs());

		UserAuditLog logEntry = UserAuditLog.builder()
						.username(username)
						.userAgent(request != null ? request.getHeader("User-Agent") : null)
						.clientIp(resolveClientIp(request))
						.requestUri(request != null ? request.getRequestURI() : null)
						.methodName(method.getDeclaringClass().getSimpleName() + "." + method.getName())
						.executionTimeMs(System.currentTimeMillis() - start)
						.paramContent(throwable == null ? params : buildErrorPayload(params, throwable))
						.build();
		auditLogAsyncService.saveAsync(logEntry);
	}

	private String buildErrorPayload(String params, Throwable throwable) {
		try {
			return objectMapper.writeValueAsString(java.util.Map.of(
							"params", objectMapper.readTree(params),
							"exception", throwable.getClass().getSimpleName()
			));
		} catch (Exception ex) {
			return "{\"params\":" + params + ",\"exception\":\"" + throwable.getClass().getSimpleName() + "\"}";
		}
	}

	private String resolveUsername() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return "anonymous";
		}
		return authentication.getName();
	}

	private String resolveClientIp(HttpServletRequest request) {
		if (request == null) {
			return null;
		}
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

	private String serializeParams(Object[] args) {
		Object[] filtered = Arrays.stream(args)
						.filter(arg -> !(arg instanceof HttpServletRequest) && !(arg instanceof HttpServletResponse))
						.toArray();
		try {
			return objectMapper.writeValueAsString(filtered);
		} catch (JsonProcessingException ex) {
			log.warn("Failed to serialize audit params, fallback to toString()");
			return Arrays.stream(filtered).map(String::valueOf).collect(Collectors.joining(","));
		}
	}
}
