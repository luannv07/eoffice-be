package vn.edu.epu.eoffice_be.common.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;

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

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<BaseResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
		return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getStatus())
						.body(BaseResponse.error(ErrorCode.UNAUTHORIZED.getMessage(), ErrorCode.UNAUTHORIZED.getCode()));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<BaseResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
		return ResponseEntity.status(ErrorCode.FORBIDDEN.getStatus())
						.body(BaseResponse.error(ErrorCode.FORBIDDEN.getMessage(), ErrorCode.FORBIDDEN.getCode()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<BaseResponse<Void>> handleGenericException(Exception ex) {
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
						.body(BaseResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), ErrorCode.INTERNAL_SERVER_ERROR.getCode()));
	}
}
