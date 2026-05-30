package vn.edu.epu.eoffice_be.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "Lỗi hệ thống", HttpStatus.INTERNAL_SERVER_ERROR),
    BAD_REQUEST("BAD_REQUEST", "Dữ liệu không hợp lệ", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", "Chưa xác thực", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "Không có quyền truy cập", HttpStatus.FORBIDDEN),
    NOT_FOUND("NOT_FOUND", "Không tìm thấy dữ liệu", HttpStatus.NOT_FOUND),
    VALIDATION_ERROR("VALIDATION_ERROR", "Lỗi validate dữ liệu", HttpStatus.BAD_REQUEST),
    BUSINESS_ERROR("BUSINESS_ERROR", "Lỗi nghiệp vụ", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
