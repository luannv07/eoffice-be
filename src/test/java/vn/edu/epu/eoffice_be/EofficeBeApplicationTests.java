package vn.edu.epu.eoffice_be;

import org.junit.jupiter.api.Test;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.controller.PublicController;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class EofficeBeApplicationTests {

	@Test
	void baseResponseSuccessFactoryWorks() {
		BaseResponse<String> response = BaseResponse.success("payload");
		assertTrue(response.isSuccess());
		assertEquals("payload", response.getData());
		assertEquals("Thành công", response.getMessage());
		assertNotNull(response.getTimestamp());
	}

	@Test
	void baseResponseErrorFactoryWorks() {
		BaseResponse<Void> response = BaseResponse.error("Lỗi", "BUSINESS_ERROR");
		assertTrue(!response.isSuccess());
		assertEquals("Lỗi", response.getMessage());
		assertEquals("BUSINESS_ERROR", response.getErrorCode());
		assertNotNull(response.getTimestamp());
	}

	@Test
	void publicControllerHealthReturnsBaseResponse() {
		PublicController controller = new PublicController();
		BaseResponse<String> response = controller.health();
		assertTrue(response.isSuccess());
		assertNotNull(response.getData());
		assertTrue(response.getData().startsWith("OK: "));
		assertNotNull(response.getTimestamp());
	}

	@Test
	void baseResponseErrorWithoutCodeWorks() {
		BaseResponse<Void> response = BaseResponse.error("only-message");
		assertFalse(response.isSuccess());
		assertEquals("only-message", response.getMessage());
		assertNull(response.getErrorCode());
		assertNotNull(response.getTimestamp());
	}
}
