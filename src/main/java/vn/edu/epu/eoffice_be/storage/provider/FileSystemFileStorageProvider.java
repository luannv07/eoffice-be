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
import java.util.stream.Stream;

/**
 * Provider lưu trữ tệp trên hệ thống tệp cục bộ (Local FileSystem).
 * Phục vụ môi trường kiểm thử (Unit/Integration test), chạy offline hoặc fallback
 * khi chưa cấu hình Telegram hay Object Storage trên đám mây.
 */
@Slf4j
@Service
public class FileSystemFileStorageProvider implements FileStorageProvider {

    private final Path rootPath;
    private final long maxReadSizeBytes;

    public FileSystemFileStorageProvider(
            @Value("${file.storage.provider.filesystem.dir:/tmp/eoffice-storage}") String storageDir,
            @Value("${file.storage.provider.filesystem.max-file-size-bytes:26214400}") long maxReadSizeBytes) {
        this.rootPath = Paths.get(storageDir).toAbsolutePath().normalize();
        this.maxReadSizeBytes = maxReadSizeBytes > 0 ? maxReadSizeBytes : 26214400L;
        try {
            Files.createDirectories(this.rootPath);
            log.info("Khởi tạo thư mục FileSystemFileStorageProvider thành công: {}", this.rootPath);
        } catch (IOException e) {
            log.error("Không thể khởi tạo thư mục lưu trữ cục bộ: {}", this.rootPath, e);
        }
    }

    public FileSystemFileStorageProvider(String storageDir) {
        this(storageDir, 26214400L);
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

        if (file.getContent().length > maxReadSizeBytes) {
            throw new FileStorageException("Kích thước tệp vượt quá giới hạn cho phép (" + maxReadSizeBytes + " bytes)");
        }

        String originalName = file.getOriginalFileName();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            String rawExt = originalName.substring(originalName.lastIndexOf("."));
            ext = rawExt.replaceAll("[^a-zA-Z0-9.]", "").toLowerCase();
        }

        String fileId = UUID.randomUUID() + ext;
        Path targetPath = resolveSecurePath(fileId);

        try {
            Files.write(targetPath, file.getContent());
            log.debug("Đã lưu tệp cục bộ: fileId={}, path={}", fileId, targetPath);
        } catch (IOException e) {
            throw new FileStorageException("Lỗi khi ghi tệp xuống ổ đĩa: " + e.getMessage(), e);
        }

        // Lưu siêu dữ liệu đi kèm để phục vụ get() và getAll()
        Path metaPath = rootPath.resolve(fileId + ".meta");
        String metaContent = (originalName != null ? originalName : "") + "\n"
                + (file.getContentType() != null ? file.getContentType() : "");
        try {
            Files.writeString(metaPath, metaContent);
        } catch (IOException e) {
            log.warn("Không thể ghi tệp metadata cục bộ {}: {}", metaPath, e.getMessage());
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
        Path targetPath = resolveSecurePath(fileId);
        if (!Files.exists(targetPath) || !Files.isReadable(targetPath)) {
            throw new FileStorageException("Không tìm thấy tệp cục bộ với id: " + fileId);
        }

        long fileSize;
        try {
            fileSize = Files.size(targetPath);
        } catch (IOException e) {
            throw new FileStorageException("Không thể xác định kích thước tệp: " + e.getMessage(), e);
        }

        // Kiểm tra giới hạn kích thước trước khi đọc mảng byte vào RAM tránh OutOfMemoryError
        if (fileSize > maxReadSizeBytes) {
            throw new FileStorageException("Kích thước tệp (" + fileSize + " bytes) vượt quá giới hạn an toàn ("
                    + maxReadSizeBytes + " bytes) để nạp vào bộ nhớ");
        }

        String originalFileName = fileId;
        String contentType = null;
        Path metaPath = rootPath.resolve(fileId + ".meta");
        if (Files.exists(metaPath)) {
            try {
                List<String> lines = Files.readAllLines(metaPath);
                if (!lines.isEmpty() && !lines.get(0).isBlank()) {
                    originalFileName = lines.get(0);
                }
                if (lines.size() > 1 && !lines.get(1).isBlank()) {
                    contentType = lines.get(1);
                }
            } catch (IOException e) {
                log.debug("Không thể đọc tệp meta {}: {}", metaPath, e.getMessage());
            }
        }
        if (contentType == null) {
            try {
                contentType = Files.probeContentType(targetPath);
            } catch (IOException ignored) {
            }
        }

        try {
            byte[] bytes = Files.readAllBytes(targetPath);
            return StoredFile.builder()
                    .id(fileId)
                    .originalFileName(originalFileName)
                    .contentType(contentType)
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
        Path targetPath = resolveSecurePath(fileId);
        try {
            Files.deleteIfExists(targetPath);
            Path metaPath = rootPath.resolve(fileId + ".meta");
            Files.deleteIfExists(metaPath);
            log.debug("Đã xóa tệp cục bộ: fileId={}", fileId);
        } catch (IOException e) {
            log.warn("Không thể xóa tệp cục bộ {}: {}", fileId, e.getMessage());
        }
    }

    private Path resolveSecurePath(String fileId) {
        if (fileId == null || fileId.isBlank() || fileId.contains("..")) {
            throw new FileStorageException("Đường dẫn tệp không hợp lệ: fileId không hợp lệ");
        }
        Path targetPath = rootPath.resolve(fileId).normalize();
        if (!targetPath.startsWith(rootPath)) {
            throw new FileStorageException("Đường dẫn tệp không hợp lệ: truy cập ngoài thư mục lưu trữ");
        }
        return targetPath;
    }

    @Override
    public List<StoredFile> getAll() {
        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(rootPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().endsWith(".meta"))
                    .map(p -> get(p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            log.error("Lỗi khi đọc danh sách tệp từ thư mục {}: {}", rootPath, e.getMessage());
            return Collections.emptyList();
        }
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
