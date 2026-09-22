package vn.edu.epu.eoffice_be.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.config.UploadProperties;
import vn.edu.epu.eoffice_be.entity.StoredFileMetadata;
import vn.edu.epu.eoffice_be.repository.StoredFileMetadataRepository;
import vn.edu.epu.eoffice_be.storage.dto.StoredFileInfo;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageProvider;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageProviderRegistry;
import vn.edu.epu.eoffice_be.storage.provider.StoredFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class FileSystemStorageService implements FileStorageService {

    private final UploadProperties uploadProperties;
    private final FileSecurityValidator securityValidator;
    private final FileStorageProviderRegistry providerRegistry;
    private final StoredFileMetadataRepository storedFileMetadataRepository;

    public FileSystemStorageService(UploadProperties uploadProperties,
                                    FileSecurityValidator securityValidator) {
        this(uploadProperties, securityValidator, null, null);
    }

    @Autowired
    public FileSystemStorageService(UploadProperties uploadProperties,
                                    FileSecurityValidator securityValidator,
                                    @Autowired(required = false) FileStorageProviderRegistry providerRegistry,
                                    @Autowired(required = false) StoredFileMetadataRepository storedFileMetadataRepository) {
        this.uploadProperties = uploadProperties;
        this.securityValidator = securityValidator;
        this.providerRegistry = providerRegistry;
        this.storedFileMetadataRepository = storedFileMetadataRepository;
    }

    @Override
    public StoredFileInfo storeFile(MultipartFile file, Long documentId) {
        // 1. Kiểm tra an toàn tệp
        securityValidator.validateFile(file);

        String originalFileName = file.getOriginalFilename();
        String extension = securityValidator.extractExtension(originalFileName).toLowerCase();

        // 2. Đọc nội dung tệp từ InputStream và tính toán mã băm SHA-256
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        String sha256Checksum;
        try (InputStream inputStream = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
                baos.write(buffer, 0, bytesRead);
            }
            sha256Checksum = HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            log.error("Thuật toán SHA-256 không khả dụng", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        } catch (IOException e) {
            log.error("Lỗi khi đọc dữ liệu tệp tải lên: {}", originalFileName, e);
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "Lỗi khi lưu trữ tệp lên hệ thống");
        }
        byte[] fileBytes = baos.toByteArray();

        String mimeType = file.getContentType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "pdf".equalsIgnoreCase(extension) ? "application/pdf" : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }

        // 3. Nếu có FileStorageProviderRegistry (Strategy Pattern), lưu qua Provider (Telegram, Filesystem...)
        if (providerRegistry != null) {
            FileStorageProvider provider = providerRegistry.getActiveProvider();
            StoredFile storedFile = StoredFile.builder()
                    .originalFileName(originalFileName)
                    .contentType(mimeType)
                    .sizeBytes(file.getSize())
                    .content(fileBytes)
                    .metadata(Map.of("documentId", String.valueOf(documentId)))
                    .build();

            StoredFile savedFile = provider.save(storedFile);

            // 4. Lưu siêu dữ liệu vào bảng file_storage_metadata
            if (storedFileMetadataRepository != null) {
                try {
                    StoredFileMetadata meta = StoredFileMetadata.builder()
                            .provider(provider.getName())
                            .fileId(savedFile.getId())
                            .originalFileName(originalFileName)
                            .contentType(mimeType)
                            .sizeBytes(file.getSize())
                            .sha256Checksum(sha256Checksum)
                            .storagePath(savedFile.getMetadata() != null ? savedFile.getMetadata().get("storagePath") : null)
                            .metadata(savedFile.getMetadata())
                            .build();
                    storedFileMetadataRepository.save(meta);
                } catch (Exception e) {
                    log.warn("Không thể lưu siêu dữ liệu vào file_storage_metadata: {}", e.getMessage());
                }
            }

            return StoredFileInfo.builder()
                    .originalFileName(originalFileName)
                    .storageFileName(savedFile.getId())
                    .storagePath(provider.getName())
                    .fileExtension(extension.toUpperCase())
                    .mimeType(mimeType)
                    .fileSizeBytes(file.getSize())
                    .sha256Checksum(sha256Checksum)
                    .build();
        }

        // Fallback: Lưu trực tiếp vào thư mục phân cấp cục bộ khi không có Provider Registry
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

        String storageFileName = UUID.randomUUID() + "." + extension;
        Path targetLocation = storageDirectory.resolve(storageFileName);

        try {
            file.transferTo(targetLocation.toFile());
        } catch (IOException e) {
            log.error("Lỗi khi ghi tệp lên đĩa: {}", targetLocation, e);
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "Lỗi khi lưu trữ tệp lên hệ thống");
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
        // 1. Thử tải qua Provider tương ứng (ví dụ storagePath = "telegram" hoặc "filesystem")
        if (providerRegistry != null) {
            try {
                FileStorageProvider provider = providerRegistry.getProvider(storagePath);
                StoredFile sf = provider.get(storageFileName);
                if (sf != null && sf.getContent() != null) {
                    return new ByteArrayResource(sf.getContent()) {
                        @Override
                        public String getFilename() {
                            return sf.getOriginalFileName() != null ? sf.getOriginalFileName() : storageFileName;
                        }
                    };
                }
            } catch (Exception e) {
                log.debug("Không thể tải tệp qua provider {}: {}", storagePath, e.getMessage());
            }
        }

        // 2. Fallback cho tệp lưu theo đường dẫn ổ đĩa vật lý
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
        if (providerRegistry != null) {
            try {
                FileStorageProvider provider = providerRegistry.getProvider(storagePath);
                if (provider != null) {
                    provider.delete(storageFileName);
                    return;
                }
            } catch (Exception e) {
                log.debug("Xóa tệp qua provider {} không thành công: {}", storagePath, e.getMessage());
            }
        }

        try {
            Path filePath = Paths.get(storagePath).resolve(storageFileName).toAbsolutePath().normalize();
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Không thể xóa tệp vật lý tại {}: {}", storagePath + "/" + storageFileName, e.getMessage());
        }
    }
}
