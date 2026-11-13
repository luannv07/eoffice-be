package vn.edu.epu.eoffice_be.storage.provider;

import lombok.Getter;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;

@Getter
public class FileStorageException extends RuntimeException {

    private final ErrorCode errorCode;

    public FileStorageException(String message) {
        this(message, ErrorCode.STORAGE_ERROR);
    }

    public FileStorageException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode != null ? errorCode : ErrorCode.STORAGE_ERROR;
    }

    public FileStorageException(String message, Throwable cause) {
        this(message, cause, ErrorCode.STORAGE_ERROR);
    }

    public FileStorageException(String message, Throwable cause, ErrorCode errorCode) {
        super(message, cause);
        this.errorCode = errorCode != null ? errorCode : ErrorCode.STORAGE_ERROR;
    }
}
