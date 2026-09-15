package vn.edu.epu.eoffice_be.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.epu.eoffice_be.storage.dto.StoredFileInfo;

public interface FileStorageService {

    StoredFileInfo storeFile(MultipartFile file, Long documentId);

    Resource loadAsResource(String storagePath, String storageFileName);

    void deleteFile(String storagePath, String storageFileName);
}
