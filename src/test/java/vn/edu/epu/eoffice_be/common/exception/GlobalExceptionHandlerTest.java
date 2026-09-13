package vn.edu.epu.eoffice_be.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@RestController
	static class ExceptionTestController {
		@GetMapping("/test/illegal-argument")
		public void throwIllegalArgument() {
			throw new IllegalArgumentException("sensitive internal server state");
		}
	}

	@Test
	void handleBusinessExceptionShouldMapStatusAndCode() {
		BusinessException ex = new BusinessException(ErrorCode.NOT_FOUND, "not-found");
		ResponseEntity<BaseResponse<Void>> response = handler.handleBusinessException(ex);

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		assertFalse(response.getBody().isSuccess());
		assertEquals("NOT_FOUND", response.getBody().getErrorCode());
		assertEquals("not-found", response.getBody().getMessage());
	}

	@Test
	void handleMethodArgumentNotValidShouldJoinFieldMessages() {
		BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "req");
		bindingResult.addError(new FieldError("req", "page", "must be positive"));
		bindingResult.addError(new FieldError("req", "size", "must be <= 1000"));

		org.springframework.core.MethodParameter parameter = new org.springframework.core.MethodParameter(GlobalExceptionHandlerTest.class.getDeclaredMethods()[0], -1);
		MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

		ResponseEntity<BaseResponse<Void>> response = handler.handleMethodArgumentNotValid(ex);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertFalse(response.getBody().isSuccess());
		assertEquals("VALIDATION_ERROR", response.getBody().getErrorCode());
		assertTrue(response.getBody().getMessage().contains("page: must be positive"));
		assertTrue(response.getBody().getMessage().contains("size: must be <= 1000"));
	}

	@Test
	void handleConstraintViolationShouldReturnValidationError() {
		ConstraintViolationException ex = new ConstraintViolationException("invalid", java.util.Set.of());
		ResponseEntity<BaseResponse<Void>> response = handler.handleConstraintViolation(ex);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals("VALIDATION_ERROR", response.getBody().getErrorCode());
		assertEquals("invalid", response.getBody().getMessage());
	}

	@Test
	void handleAuthenticationExceptionShouldReturnUnauthorized() {
		ResponseEntity<BaseResponse<Void>> response =
						handler.handleAuthenticationException(new org.springframework.security.authentication.InsufficientAuthenticationException("unauthorized"));

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertEquals("UNAUTHORIZED", response.getBody().getErrorCode());
		assertEquals(ErrorCode.UNAUTHORIZED.getMessage(), response.getBody().getMessage());
	}

	@Test
	void handleAccessDeniedShouldReturnForbidden() {
		ResponseEntity<BaseResponse<Void>> response =
						handler.handleAccessDenied(new AccessDeniedException("denied"));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		assertEquals("FORBIDDEN", response.getBody().getErrorCode());
		assertEquals(ErrorCode.FORBIDDEN.getMessage(), response.getBody().getMessage());
	}

	@Test
	void handleGenericExceptionShouldReturnInternalServerError() {
		ResponseEntity<BaseResponse<Void>> response =
						handler.handleGenericException(new RuntimeException("boom"));

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertEquals("INTERNAL_SERVER_ERROR", response.getBody().getErrorCode());
		assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), response.getBody().getMessage());
	}

	@Test
	void handleMethodArgumentNotValidShouldWorkWithSingleFieldError() {
		BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "req");
		bindingResult.addError(new FieldError("req", "name", "must not be blank"));

		org.springframework.core.MethodParameter parameter = new org.springframework.core.MethodParameter(GlobalExceptionHandlerTest.class.getDeclaredMethods()[0], -1);
		MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

		ResponseEntity<BaseResponse<Void>> response = handler.handleMethodArgumentNotValid(ex);
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals("name: must not be blank", response.getBody().getMessage());
	}

	@Test
	void handleBusinessExceptionShouldUseErrorCodeDefaultStatus() {
		BusinessException ex = new BusinessException(ErrorCode.UNAUTHORIZED);
		ResponseEntity<BaseResponse<Void>> response = handler.handleBusinessException(ex);
		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertEquals("UNAUTHORIZED", response.getBody().getErrorCode());
	}

	@Test
	void unexpectedIllegalArgumentExceptionShouldFallThroughTo500AndNotExposeRawMessage() {
		IllegalArgumentException ex = new IllegalArgumentException("sensitive internal server state");
		ResponseEntity<BaseResponse<Void>> response = handler.handleGenericException(ex);

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertFalse(response.getBody().isSuccess());
		assertEquals("INTERNAL_SERVER_ERROR", response.getBody().getErrorCode());
		assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), response.getBody().getMessage());
		assertFalse(response.getBody().getMessage().contains("sensitive internal server state"));
	}

	@Test
	void mockMvc_unexpectedIllegalArgumentExceptionShouldDispatchTo500AndNotExposeRawMessage() throws Exception {
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ExceptionTestController())
				.setControllerAdvice(handler)
				.build();

		mockMvc.perform(get("/test/illegal-argument"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"))
				.andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()))
				.andExpect(content().string(not(containsString("sensitive internal server state"))));
	}
}
