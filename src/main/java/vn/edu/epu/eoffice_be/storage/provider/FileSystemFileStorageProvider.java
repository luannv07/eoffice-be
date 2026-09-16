package vn.edu.epu.eoffice_be.storage.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provider lưu trữ tệp trên hệ thống tệp cục bộ (Local FileSystem).
 * Phục vụ môi trường kiểm thử (Unit/Integration test), chạy offline hoặc fallback
 * khi chưa cấu hình Telegram hay Object Storage trên đám mây.
 */
@Slf4j
@Service
public class FileSystemFileStorageProvider implements FileStorageProvider {

    private final Path rootPath;

    public FileSystemFileStorageProvider(@Value("${file.storage.provider.filesystem.dir:/tmp/eoffice-storage}") String storageDir) {
        this.rootPath = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootPath);
            log.info("Khởi tạo thư mục FileSystemFileStorageProvider thành công: {}", this.rootPath);
        } catch (IOException e) {
            log.error("Không thể khởi tạo thư mục lưu trữ cục bộ: {}", this.rootPath, e);
        }
    }

    @Override
    public String getName() {
        return "filesystem";
    }

    @Override
    public StoredFile save(StoredFile file) {
        if (file == null || file.getContent() == null || file.getContent().length == 0) {
            throw new FileStorageException("Nội dung tệp không được để trống");
        }

        String originalName = file.getOriginalFileName();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }

        String fileId = UUID.randomUUID() + ext;
        Path targetPath = rootPath.resolve(fileId).normalize();

        try {
            Files.write(targetPath, file.getContent());
            log.debug("Đã lưu tệp cục bộ: fileId={}, path={}", fileId, targetPath);
        } catch (IOException e) {
            throw new FileStorageException("Lỗi khi ghi tệp xuống ổ đĩa: " + e.getMessage(), e);
        }

        return StoredFile.builder()
                .id(fileId)
                .originalFileName(originalName)
                .contentType(file.getContentType())
                .sizeBytes(file.getContent().length)
                .metadata(Map.of("storagePath", targetPath.toString()))
                .build();
    }

    @Override
    public StoredFile get(String fileId) {
        Path targetPath = rootPath.resolve(fileId).normalize();
        if (!Files.exists(targetPath) || !Files.isReadable(targetPath)) {
            throw new FileStorageException("Không tìm thấy tệp cục bộ với id: " + fileId);
        }

        try {
            byte[] bytes = Files.readAllBytes(targetPath);
            return StoredFile.builder()
                    .id(fileId)
                    .content(bytes)
                    .sizeBytes(bytes.length)
                    .metadata(Map.of("storagePath", targetPath.toString()))
                    .build();
        } catch (IOException e) {
            throw new FileStorageException("Lỗi khi đọc tệp cục bộ: " + e.getMessage(), e);
        }
    }

    @Override
    public StoredFile update(String fileId, StoredFile file) {
        delete(fileId);
        return save(file);
    }

    @Override
    public void delete(String fileId) {
        Path targetPath = rootPath.resolve(fileId).normalize();
        try {
            Files.deleteIfExists(targetPath);
            log.debug("Đã xóa tệp cục bộ: fileId={}", fileId);
        } catch (IOException e) {
            log.warn("Không thể xóa tệp cục bộ {}: {}", fileId, e.getMessage());
        }
    }

    @Override
    public List<StoredFile> getAll() {
        return Collections.emptyList();
    }

    @Override
    public StoredFile getDetail(String fileId) {
        return get(fileId);
    }

    @Override
    public void deleteMany(List<String> fileIds) {
        if (fileIds != null) {
            fileIds.forEach(this::delete);
        }
    }
}
