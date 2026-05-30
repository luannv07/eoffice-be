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
    void exceptionShouldAlwaysContainErrorCode() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            BusinessException ex = new BusinessException(errorCode);
            assertNotNull(ex.getErrorCode());
        }
    }
}
