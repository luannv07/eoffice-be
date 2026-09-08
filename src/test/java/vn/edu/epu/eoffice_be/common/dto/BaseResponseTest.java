package vn.edu.epu.eoffice_be.common.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BaseResponseTest {

	@Test
	void successFactoryShouldPopulateDefaultMessage() {
		BaseResponse<String> response = BaseResponse.success("data");
		assertTrue(response.isSuccess());
		assertEquals("data", response.getData());
		assertEquals("Thành công", response.getMessage());
		assertNotNull(response.getTimestamp());
	}

	@Test
	void errorFactoryShouldPopulateErrorCode() {
		BaseResponse<Void> response = BaseResponse.error("error", "BAD_REQUEST");
		assertFalse(response.isSuccess());
		assertEquals("error", response.getMessage());
		assertEquals("BAD_REQUEST", response.getErrorCode());
		assertNotNull(response.getTimestamp());
	}

	@Test
	void successFactoryWithCustomMessageShouldUseProvidedMessage() {
		BaseResponse<String> response = BaseResponse.success("data", "custom");
		assertTrue(response.isSuccess());
		assertEquals("data", response.getData());
		assertEquals("custom", response.getMessage());
		assertNotNull(response.getTimestamp());
	}

	@Test
	void errorFactoryWithoutErrorCodeShouldLeaveErrorCodeNull() {
		BaseResponse<Void> response = BaseResponse.error("error-only");
		assertFalse(response.isSuccess());
		assertEquals("error-only", response.getMessage());
		assertNull(response.getErrorCode());
		assertNotNull(response.getTimestamp());
	}

	@Test
	void builderShouldAllowManualResponseConstruction() {
		BaseResponse<Integer> response = BaseResponse.<Integer>builder()
						.success(true)
						.data(123)
						.message("ok")
						.errorCode(null)
						.timestamp(java.time.Instant.now())
						.build();
		assertTrue(response.isSuccess());
		assertEquals(123, response.getData());
		assertEquals("ok", response.getMessage());
		assertNull(response.getErrorCode());
		assertNotNull(response.getTimestamp());
	}
}
