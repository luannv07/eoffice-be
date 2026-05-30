package vn.edu.epu.eoffice_be.controller;

import org.junit.jupiter.api.Test;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PublicControllerTest {

    private final PublicController controller = new PublicController();

    @Test
    void healthShouldReturnSuccessResponse() {
        BaseResponse<String> response = controller.health();
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getTimestamp());
        assertNotNull(response.getData());
        assertTrue(response.getData().startsWith("OK: "));
    }

    @Test
    void healthShouldUseDefaultSuccessMessage() {
        BaseResponse<String> response = controller.health();
        assertEquals("Thành công", response.getMessage());
    }

    @Test
    void healthShouldNotReturnErrorCode() {
        BaseResponse<String> response = controller.health();
        assertNull(response.getErrorCode());
    }
}
