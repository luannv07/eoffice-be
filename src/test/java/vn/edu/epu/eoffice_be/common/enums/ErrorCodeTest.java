package vn.edu.epu.eoffice_be.common.enums;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeTest {

	@Test
	void internalServerErrorShouldMapToHttp500() {
		assertEquals("INTERNAL_SERVER_ERROR", ErrorCode.INTERNAL_SERVER_ERROR.getCode());
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.getStatus());
	}

	@Test
	void allErrorCodesShouldHaveNonBlankCodeMessageAndStatus() {
		for (ErrorCode errorCode : ErrorCode.values()) {
			assertNotNull(errorCode.getCode());
			assertNotNull(errorCode.getMessage());
			assertNotNull(errorCode.getStatus());
			assertTrue(!errorCode.getCode().isBlank());
			assertTrue(!errorCode.getMessage().isBlank());
		}
	}
}
