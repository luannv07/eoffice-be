package vn.edu.epu.eoffice_be.storage.provider;

import vn.edu.epu.eoffice_be.common.enums.ErrorCode;

public class FileNotFoundStorageException extends FileStorageException {

    public FileNotFoundStorageException(String message) {
        super(message, ErrorCode.NOT_FOUND);
    }

    public FileNotFoundStorageException(String message, Throwable cause) {
        super(message, cause, ErrorCode.NOT_FOUND);
    }
}

