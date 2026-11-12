package vn.edu.epu.eoffice_be.common.exception;

import org.junit.jupiter.api.Test;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BusinessExceptionTest {

	@Test
	void constructorWithErrorCodeShouldUseDefaultMessage() {
		BusinessException ex = new BusinessException(ErrorCode.NOT_FOUND);
		assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
		assertEquals(ErrorCode.NOT_FOUND.getMessage(), ex.getMessage());
	}

	@Test
	void constructorWithCustomMessageShouldOverrideMessage() {
		BusinessException ex = new BusinessException(ErrorCode.BAD_REQUEST, "custom");
		assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
		assertEquals("custom", ex.getMessage());
	}

	@Test
	void constructorWithCauseShouldPreserveCause() {
		Exception cause = new IllegalStateException("root cause");
		BusinessException ex = new BusinessException(ErrorCode.BAD_REQUEST, "custom with cause", cause);
		assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
		assertEquals("custom with cause", ex.getMessage());
		assertEquals(cause, ex.getCause());

		BusinessException exWithDefaultMsg = new BusinessException(ErrorCode.BAD_REQUEST, cause);
		assertEquals(ErrorCode.BAD_REQUEST, exWithDefaultMsg.getErrorCode());
		assertEquals(ErrorCode.BAD_REQUEST.getMessage(), exWithDefaultMsg.getMessage());
		assertEquals(cause, exWithDefaultMsg.getCause());
	}

	@Test
	void exceptionShouldAlwaysContainErrorCode() {
		for (ErrorCode errorCode : ErrorCode.values()) {
			BusinessException ex = new BusinessException(errorCode);
			assertNotNull(ex.getErrorCode());
		}
	}
}
