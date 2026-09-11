package vn.edu.epu.eoffice_be.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.service.FileStorageService;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageServiceImpl implements FileStorageService {

    @Value("${eoffice.storage.base-path:./uploads}")
    private String basePathStr;

    @Value("${eoffice.storage.max-file-size-bytes:20971520}")
    private long maxFileSizeBytes = 20 * 1024 * 1024L; // 20 MB giới hạn an toàn khi nạp byte[]

    private Path rootStoragePath;

    @PostConstruct
    public void init() {
        this.rootStoragePath = Paths.get(basePathStr).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootStoragePath);
            this.rootStoragePath = this.rootStoragePath.toRealPath();
            log.info("Khởi tạo thư mục lưu trữ file thành công: {}", this.rootStoragePath);
        } catch (IOException e) {
            log.error("Không thể tạo thư mục lưu trữ file: {}", this.rootStoragePath, e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public String storeFile(byte[] content, String originalFilename, String subDirectory) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        String safeFileName = "file.pdf";
        if (originalFilename != null && !originalFilename.isBlank()) {
            try {
                String normalizedName = originalFilename.replace('\\', '/');
                Path fileNamePath = Paths.get(normalizedName).getFileName();
                if (fileNamePath != null && !fileNamePath.toString().isBlank()) {
                    safeFileName = fileNamePath.toString();
                }
            } catch (Exception ignored) {
                // fallback to default "file.pdf"
            }
        }
        String storedFileName = UUID.randomUUID() + "_" + safeFileName;

        Path subDirTarget = this.rootStoragePath.resolve(subDirectory == null ? "" : subDirectory).normalize();

        // Chống tấn công Path Traversal
        if (!subDirTarget.startsWith(this.rootStoragePath)) {
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }

        try {
            Files.createDirectories(subDirTarget);
            Path realRoot = this.rootStoragePath.toRealPath();
            Path realSubDir = subDirTarget.toRealPath();
            if (!realSubDir.startsWith(realRoot)) {
                log.error("Phát hiện symlink path traversal khi lưu file: subDirTarget={}, realSubDir={}", subDirTarget, realSubDir);
                throw new BusinessException(ErrorCode.STORAGE_ERROR);
            }

            Path targetFilePath = realSubDir.resolve(storedFileName);
            if (Files.isSymbolicLink(targetFilePath)) {
                log.error("Phát hiện targetFilePath là symbolic link: {}", targetFilePath);
                throw new BusinessException(ErrorCode.STORAGE_ERROR);
            }

            Files.write(targetFilePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            if (!targetFilePath.toRealPath().startsWith(realRoot)) {
                Files.deleteIfExists(targetFilePath);
                log.error("Phát hiện symlink path traversal sau khi ghi file: targetFilePath={}", targetFilePath);
                throw new BusinessException(ErrorCode.STORAGE_ERROR);
            }

            String relativePath = (subDirectory == null || subDirectory.isBlank())
                    ? storedFileName
                    : Paths.get(subDirectory, storedFileName).toString().replace("\\", "/");

            log.info("Lưu file thành công: {}", relativePath);
            return relativePath.replace("\\", "/");
        } catch (IOException e) {
            log.error("Lỗi khi ghi file vào đĩa: {}", storedFileName, e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public byte[] loadFileBytes(String relativePath) {
        Path realTarget = resolveAndValidatePath(relativePath);

        try {
            long fileSize = Files.size(realTarget);
            if (fileSize > maxFileSizeBytes) {
                log.error("Kích thước file ({} bytes) vượt quá giới hạn an toàn ({} bytes) khi nạp vào bộ nhớ: {}",
                        fileSize, maxFileSizeBytes, realTarget);
                throw new BusinessException(ErrorCode.STORAGE_ERROR);
            }

            return Files.readAllBytes(realTarget);
        } catch (IOException e) {
            log.error("Lỗi khi đọc file: {}", realTarget, e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public InputStream loadFileInputStream(String relativePath) {
        Path realTarget = resolveAndValidatePath(relativePath);

        try {
            return Files.newInputStream(realTarget);
        } catch (IOException e) {
            log.error("Lỗi khi mở luồng đọc file: {}", realTarget, e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public Resource loadFileAsResource(String relativePath) {
        Path realTarget = resolveAndValidatePath(relativePath);

        try {
            Resource resource = new UrlResource(realTarget.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new BusinessException(ErrorCode.DOCUMENT_FILE_MISSING);
            }
        } catch (MalformedURLException e) {
            log.error("Lỗi URL không hợp lệ khi nạp resource: {}", realTarget, e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
    }

    private Path resolveAndValidatePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new BusinessException(ErrorCode.DOCUMENT_FILE_MISSING);
        }

        Path targetPath = this.rootStoragePath.resolve(relativePath).normalize();

        // Chống tấn công Path Traversal
        if (!targetPath.startsWith(this.rootStoragePath)) {
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }

        if (!Files.exists(targetPath) || !Files.isReadable(targetPath)) {
            log.error("File không tồn tại hoặc không đọc được: {}", targetPath);
            throw new BusinessException(ErrorCode.DOCUMENT_FILE_MISSING);
        }

        try {
            Path realRoot = this.rootStoragePath.toRealPath();
            Path realTarget = targetPath.toRealPath();
            if (!realTarget.startsWith(realRoot)) {
                log.error("Phát hiện symlink path traversal khi truy cập file: targetPath={}, realTarget={}", targetPath, realTarget);
                throw new BusinessException(ErrorCode.STORAGE_ERROR);
            }

            return realTarget;
        } catch (IOException e) {
            log.error("Lỗi khi xác thực đường dẫn file: {}", targetPath, e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public String computeSha256(byte[] content) {
        if (content == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(content);
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}