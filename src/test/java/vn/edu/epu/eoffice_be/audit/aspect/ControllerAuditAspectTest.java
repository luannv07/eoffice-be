package vn.edu.epu.eoffice_be.audit.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.edu.epu.eoffice_be.audit.entity.UserAuditLog;
import vn.edu.epu.eoffice_be.audit.service.AuditLogAsyncService;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ControllerAuditAspectTest {

	private final AuditLogAsyncService auditLogAsyncService = mock(AuditLogAsyncService.class);
	private final ControllerAuditAspect aspect = new ControllerAuditAspect(auditLogAsyncService, new ObjectMapper());

	@AfterEach
	void cleanup() {
		RequestContextHolder.resetRequestAttributes();
		SecurityContextHolder.clearContext();
	}

	@Test
	void shouldSkipWhenControllerIsNotAnnotated() throws Throwable {
		ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
		MethodSignature methodSignature = mock(MethodSignature.class);
		Method method = PlainController.class.getDeclaredMethod("plainMethod");

		when(joinPoint.getSignature()).thenReturn(methodSignature);
		when(joinPoint.getTarget()).thenReturn(new PlainController());
		when(methodSignature.getMethod()).thenReturn(method);
		when(joinPoint.proceed()).thenReturn("ok");

		Object result = aspect.auditController(joinPoint);
		assertEquals("ok", result);
		verify(auditLogAsyncService, never()).saveAsync(any(UserAuditLog.class));
	}

	@Test
	void shouldWriteAuditLogForAnnotatedController() throws Throwable {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public/health");
		request.addHeader("User-Agent", "JUnit");
		request.setRemoteAddr("127.0.0.1");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
		SecurityContextHolder.getContext().setAuthentication(
						UsernamePasswordAuthenticationToken.authenticated("tester", "n/a", List.of())
		);

		ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
		MethodSignature methodSignature = mock(MethodSignature.class);
		Method method = AnnotatedController.class.getDeclaredMethod("health", String.class);

		when(joinPoint.getSignature()).thenReturn(methodSignature);
		when(joinPoint.getTarget()).thenReturn(new AnnotatedController());
		when(methodSignature.getMethod()).thenReturn(method);
		when(joinPoint.getArgs()).thenReturn(new Object[]{"abc"});
		when(joinPoint.proceed()).thenReturn("ok");

		aspect.auditController(joinPoint);

		verify(auditLogAsyncService).saveAsync(any(UserAuditLog.class));
	}

	@Test
	void shouldWriteAuditLogWhenExceptionThrown() throws Throwable {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public/health");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

		ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
		MethodSignature methodSignature = mock(MethodSignature.class);
		Method method = AnnotatedController.class.getDeclaredMethod("health", String.class);

		when(joinPoint.getSignature()).thenReturn(methodSignature);
		when(joinPoint.getTarget()).thenReturn(new AnnotatedController());
		when(methodSignature.getMethod()).thenReturn(method);
		when(joinPoint.getArgs()).thenReturn(new Object[]{"x"});
		when(joinPoint.proceed()).thenThrow(new RuntimeException("boom"));

		assertThrows(RuntimeException.class, () -> aspect.auditController(joinPoint));
		verify(auditLogAsyncService).saveAsync(any(UserAuditLog.class));
	}

	static class PlainController {
		public String plainMethod() {
			return "ok";
		}
	}

	@Auditable
	static class AnnotatedController {
		public String health(String value) {
			return value;
		}
	}
}
