package vn.edu.epu.eoffice_be.common.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.storage.provider.FileNotFoundStorageException;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageException;
import vn.edu.epu.eoffice_be.storage.provider.InvalidFileStorageException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResponse<Void>> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(BaseResponse.error(ex.getMessage(), errorCode.getCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(BaseResponse.error(message, ErrorCode.VALIDATION_ERROR.getCode()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<BaseResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(BaseResponse.error(ex.getMessage(), ErrorCode.VALIDATION_ERROR.getCode()));
    }

    @ExceptionHandler({AuthenticationException.class, org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class})
    public ResponseEntity<BaseResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failure: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getStatus())
                .body(BaseResponse.error(ErrorCode.UNAUTHORIZED.getMessage(), ErrorCode.UNAUTHORIZED.getCode()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<BaseResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getStatus())
                .body(BaseResponse.error(ErrorCode.FORBIDDEN.getMessage(), ErrorCode.FORBIDDEN.getCode()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<BaseResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(ErrorCode.FILE_SIZE_EXCEEDED.getStatus())
                .body(BaseResponse.error(ErrorCode.FILE_SIZE_EXCEEDED.getMessage(), ErrorCode.FILE_SIZE_EXCEEDED.getCode()));
    }

    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<BaseResponse<Void>> handleMultipartException(Exception ex) {
        log.error("Invalid file upload request: ", ex);
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getStatus())
                .body(BaseResponse.error("Yêu cầu upload file không hợp lệ hoặc kích thước vượt quá giới hạn", ErrorCode.BAD_REQUEST.getCode()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<BaseResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("Data integrity violation: ", ex);
        return ResponseEntity.status(ErrorCode.CONFLICT.getStatus())
                .body(BaseResponse.error("Dữ liệu bị trùng lặp hoặc vi phạm ràng buộc toàn vẹn cơ sở dữ liệu", ErrorCode.CONFLICT.getCode()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<BaseResponse<Void>> handleMissingServletRequestParameter(MissingServletRequestParameterException ex) {
        log.warn("Missing request parameter: {}", ex.getParameterName());
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getStatus())
                .body(BaseResponse.error("Thiếu tham số bắt buộc: " + ex.getParameterName(), ErrorCode.BAD_REQUEST.getCode()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<BaseResponse<Void>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Method argument type mismatch: parameter '{}' with value '{}'", ex.getName(), ex.getValue());
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getStatus())
                .body(BaseResponse.error("Tham số '" + ex.getName() + "' không hợp lệ", ErrorCode.BAD_REQUEST.getCode()));
    }

    @ExceptionHandler(FileNotFoundStorageException.class)
    public ResponseEntity<BaseResponse<Void>> handleFileNotFoundStorageException(FileNotFoundStorageException ex) {
        log.warn("File not found in storage: {}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getStatus())
                .body(BaseResponse.error("Không tìm thấy tệp tin yêu cầu", ErrorCode.NOT_FOUND.getCode()));
    }

    @ExceptionHandler(InvalidFileStorageException.class)
    public ResponseEntity<BaseResponse<Void>> handleInvalidFileStorageException(InvalidFileStorageException ex) {
        log.warn("Invalid file storage request: {}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getStatus())
                .body(BaseResponse.error(ex.getMessage() != null ? ex.getMessage() : "Yêu cầu tệp không hợp lệ",
                        ErrorCode.BAD_REQUEST.getCode()));
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<BaseResponse<Void>> handleFileStorageException(FileStorageException ex) {
        log.error("File storage error: ", ex);
        ErrorCode code = ex.getErrorCode() != null ? ex.getErrorCode() : ErrorCode.STORAGE_ERROR;
        String message = switch (code) {
            case NOT_FOUND -> ex.getMessage() != null ? ex.getMessage() : "Không tìm thấy tệp tin yêu cầu";
            case BAD_REQUEST -> ex.getMessage() != null ? ex.getMessage() : "Yêu cầu tệp không hợp lệ";
            default -> "Lỗi lưu trữ tệp. Vui lòng thử lại sau";
        };
        return ResponseEntity.status(code.getStatus())
                .body(BaseResponse.error(message, code.getCode()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<BaseResponse<Void>> handleIllegalStateException(IllegalStateException ex) {
        if (ex.getMessage() != null && ex.getMessage().contains("User đã ký")) {
            log.warn("Cảnh báo chữ ký trùng lặp: {}", ex.getMessage());
            return ResponseEntity.status(ErrorCode.BAD_REQUEST.getStatus())
                    .body(BaseResponse.error("Người dùng đã thực hiện ký trên tài liệu này rồi, không được phép ký đè lần 2!", ErrorCode.BAD_REQUEST.getCode()));
        }
        log.error("Unhandled IllegalStateException", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(BaseResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), ErrorCode.INTERNAL_SERVER_ERROR.getCode()));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<Void>> handleHttpMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON or unrecognized property: {}", ex.getMessage());
        String message = "Dữ liệu JSON gửi lên không hợp lệ hoặc chứa trường không đúng định dạng";
        if (ex.getCause() instanceof com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException upe) {
            message = "Trường '" + upe.getPropertyName() + "' không được hỗ trợ trong API này";
        }
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getStatus())
                .body(BaseResponse.error(message, ErrorCode.BAD_REQUEST.getCode()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<BaseResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .body(BaseResponse.error("Phương thức HTTP '" + ex.getMethod() + "' không được hỗ trợ cho endpoint này", ErrorCode.METHOD_NOT_ALLOWED.getCode()));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<BaseResponse<Void>> handleNotFound(Exception ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getStatus())
                .body(BaseResponse.error("Không tìm thấy tài nguyên yêu cầu", ErrorCode.NOT_FOUND.getCode()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(BaseResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), ErrorCode.INTERNAL_SERVER_ERROR.getCode()));
    }
}