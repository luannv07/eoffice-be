package vn.edu.epu.eoffice_be.common.exception;

import vn.edu.epu.eoffice_be.common.enums.ErrorCode;

public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public BusinessException(ErrorCode errorCode, Throwable cause) {
		super(errorCode != null ? errorCode.getMessage() : null, cause);
		this.errorCode = errorCode;
	}

	public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}
}
