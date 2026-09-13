package vn.edu.epu.eoffice_be.storage.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StoredFileInfo {

    private String originalFileName;
    private String storageFileName;
    private String storagePath;
    private String fileExtension;
    private String mimeType;
    private long fileSizeBytes;
    private String sha256Checksum;
}
