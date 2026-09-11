package vn.edu.epu.eoffice_be.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.config.UploadProperties;
import vn.edu.epu.eoffice_be.storage.dto.StoredFileInfo;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileSystemStorageService implements FileStorageService {

    private final UploadProperties uploadProperties;
    private final FileSecurityValidator securityValidator;

    @Override
    public StoredFileInfo storeFile(MultipartFile file, Long documentId) {
        // 1. Kiểm tra an toàn tệp
        securityValidator.validateFile(file);

        String originalFileName = file.getOriginalFilename();
        String extension = securityValidator.extractExtension(originalFileName).toLowerCase();

        // 2. Tạo đường dẫn lưu trữ theo thư mục phân cấp năm/tháng/documentId
        LocalDate now = LocalDate.now(ZoneId.systemDefault());
        String relativePath = String.format("%s/%d/%02d/%d",
                uploadProperties.getDir(), now.getYear(), now.getMonthValue(), documentId);
        Path storageDirectory = Paths.get(relativePath).toAbsolutePath().normalize();

        try {
            Files.createDirectories(storageDirectory);
        } catch (IOException e) {
            log.error("Không thể tạo thư mục lưu trữ: {}", storageDirectory, e);
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "Không thể khởi tạo thư mục lưu trữ");
        }

        // 3. Tạo tên tệp ngẫu nhiên UUID
        String storageFileName = UUID.randomUUID() + "." + extension;
        Path targetLocation = storageDirectory.resolve(storageFileName);

        // 4. Lưu tệp và tính toán mã băm SHA-256
        String sha256Checksum;
        try (InputStream inputStream = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            sha256Checksum = HexFormat.of().formatHex(digest.digest());

            // Ghi tệp xuống ổ đĩa
            file.transferTo(targetLocation.toFile());
        } catch (NoSuchAlgorithmException e) {
            log.error("Thuật toán SHA-256 không khả dụng", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        } catch (IOException e) {
            log.error("Lỗi khi ghi tệp lên đĩa: {}", targetLocation, e);
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "Lỗi khi lưu trữ tệp lên hệ thống");
        }

        String mimeType = file.getContentType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "pdf".equalsIgnoreCase(extension) ? "application/pdf" : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }

        return StoredFileInfo.builder()
                .originalFileName(originalFileName)
                .storageFileName(storageFileName)
                .storagePath(relativePath)
                .fileExtension(extension.toUpperCase())
                .mimeType(mimeType)
                .fileSizeBytes(file.getSize())
                .sha256Checksum(sha256Checksum)
                .build();
    }

    @Override
    public Resource loadAsResource(String storagePath, String storageFileName) {
        try {
            Path filePath = Paths.get(storagePath).resolve(storageFileName).toAbsolutePath().normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new BusinessException(ErrorCode.DOCUMENT_ATTACHMENT_NOT_FOUND, "Không tìm thấy tệp đính kèm trên hệ thống lưu trữ");
            }
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.DOCUMENT_ATTACHMENT_NOT_FOUND, "Đường dẫn tệp không hợp lệ");
        }
    }

    @Override
    public void deleteFile(String storagePath, String storageFileName) {
        try {
            Path filePath = Paths.get(storagePath).resolve(storageFileName).toAbsolutePath().normalize();
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Không thể xóa tệp vật lý tại {}: {}", storagePath + "/" + storageFileName, e.getMessage());
        }
    }
}
