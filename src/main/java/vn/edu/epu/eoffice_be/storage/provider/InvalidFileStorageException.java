package vn.edu.epu.eoffice_be.storage.provider;

import vn.edu.epu.eoffice_be.common.enums.ErrorCode;

public class InvalidFileStorageException extends FileStorageException {

    public InvalidFileStorageException(String message) {
        super(message, ErrorCode.BAD_REQUEST);
    }

    public InvalidFileStorageException(String message, Throwable cause) {
        super(message, cause, ErrorCode.BAD_REQUEST);
    }
}

