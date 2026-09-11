package vn.edu.epu.eoffice_be.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.service.FileStorageService;

import java.io.IOException;
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

    private Path rootStoragePath;

    @PostConstruct
    public void init() {
        this.rootStoragePath = Paths.get(basePathStr).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootStoragePath);
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

        String safeFileName = originalFilename == null ? "file.pdf" : Paths.get(originalFilename).getFileName().toString();
        String storedFileName = UUID.randomUUID() + "_" + safeFileName;

        Path subDirTarget = this.rootStoragePath.resolve(subDirectory == null ? "" : subDirectory).normalize();

        // Chống tấn công Path Traversal
        if (!subDirTarget.startsWith(this.rootStoragePath)) {
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }

        try {
            Files.createDirectories(subDirTarget);
            Path targetFilePath = subDirTarget.resolve(storedFileName);
            Files.write(targetFilePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

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
            return Files.readAllBytes(targetPath);
        } catch (IOException e) {
            log.error("Lỗi khi đọc file: {}", targetPath, e);
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