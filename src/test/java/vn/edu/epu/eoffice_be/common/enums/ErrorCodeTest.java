package vn.edu.epu.eoffice_be.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeTest {

    @Test
    @DisplayName("ErrorCode: DOCUMENT_NOT_FOUND phải có mã và thông điệp chuẩn xác")
    void testDocumentNotFoundContract() {
        ErrorCode code = ErrorCode.DOCUMENT_NOT_FOUND;
        assertEquals("DOCUMENT_NOT_FOUND", code.getCode());
        assertEquals("Không tìm thấy văn bản", code.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, code.getStatus());
    }

    @Test
    @DisplayName("ErrorCode: DOCUMENT_INVALID_STATUS bảo đảm tương thích ngược với clients hiện tại")
    void testDocumentInvalidStatusContract() {
        ErrorCode legacyCode = ErrorCode.DOCUMENT_INVALID_STATUS;
        assertEquals("DOCUMENT_INVALID_STATUS", legacyCode.getCode());
        assertEquals("Trạng thái văn bản không hợp lệ cho thao tác này", legacyCode.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, legacyCode.getStatus());
    }

    @Test
    @DisplayName("ErrorCode: INVALID_DOCUMENT_STATUS cung cấp mã chuẩn hóa mới")
    void testInvalidDocumentStatusContract() {
        ErrorCode newCode = ErrorCode.INVALID_DOCUMENT_STATUS;
        assertEquals("INVALID_DOCUMENT_STATUS", newCode.getCode());
        assertEquals("Trạng thái văn bản không hợp lệ", newCode.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, newCode.getStatus());
    }
}
