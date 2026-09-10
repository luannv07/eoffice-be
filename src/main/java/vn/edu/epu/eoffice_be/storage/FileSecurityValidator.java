package vn.edu.epu.eoffice_be.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.config.UploadProperties;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileSecurityValidator {

    private final UploadProperties uploadProperties;

    // PDF Magic Bytes: %PDF- (0x25, 0x50, 0x44, 0x46, 0x2D)
    private static final byte[] PDF_MAGIC_BYTES = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D};

    // ZIP/DOCX Magic Bytes: PK\x03\x04 (0x50, 0x4B, 0x03, 0x04)
    private static final byte[] ZIP_MAGIC_BYTES = new byte[]{0x50, 0x4B, 0x03, 0x04};

    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            ".exe", ".bat", ".cmd", ".sh", ".ps1", ".vbs", ".dll", ".jar", ".msi", ".com"
    );

    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Tệp tải lên không được để trống");
        }

        // 1. Kiểm tra kích thước
        if (file.getSize() > uploadProperties.getMaxSizeBytes()) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED,
                    "Dung lượng tệp vượt quá giới hạn cho phép (" + (uploadProperties.getMaxSizeBytes() / (1024 * 1024)) + "MB)");
        }

        // 2. Kiểm tra tên tệp & Path Traversal
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Tên tệp không hợp lệ");
        }
        if (originalFileName.contains("..") || originalFileName.contains("/") || originalFileName.contains("\\")) {
            throw new BusinessException(ErrorCode.FILE_UNSAFE, "Tên tệp chứa ký tự đường dẫn không an toàn");
        }

        // 3. Kiểm tra phần mở rộng (Extension Whitelist)
        String extension = extractExtension(originalFileName).toLowerCase();
        boolean isAllowed = uploadProperties.getAllowedExtensions() != null && uploadProperties.getAllowedExtensions().stream()
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .anyMatch(ext -> ext.equals(extension));
        if (!isAllowed) {
            throw new BusinessException(ErrorCode.FILE_INVALID_FORMAT,
                    "Chỉ chấp nhận tệp có định dạng DOCX hoặc PDF");
        }

        // 4. Kiểm tra Header Magic Bytes & Quét an toàn
        try {
            byte[] fileBytes = file.getBytes();
            if ("pdf".equalsIgnoreCase(extension)) {
                validatePdf(fileBytes);
            } else if ("docx".equalsIgnoreCase(extension)) {
                validateDocx(fileBytes);
            } else {
                throw new BusinessException(ErrorCode.FILE_INVALID_FORMAT);
            }
        } catch (IOException e) {
            log.error("Lỗi khi đọc dữ liệu tệp tải lên: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "Không thể đọc dữ liệu tệp");
        }
    }

    public String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    private void validatePdf(byte[] bytes) {
        if (bytes.length < PDF_MAGIC_BYTES.length) {
            throw new BusinessException(ErrorCode.FILE_INVALID_FORMAT, "Tệp PDF không hợp lệ");
        }
        byte[] header = Arrays.copyOfRange(bytes, 0, PDF_MAGIC_BYTES.length);
        if (!Arrays.equals(PDF_MAGIC_BYTES, header)) {
            throw new BusinessException(ErrorCode.FILE_INVALID_FORMAT, "Chữ ký tệp (magic bytes) không phải định dạng PDF hợp lệ");
        }

        // Quét nội dung nguy hiểm trong PDF (ví dụ: nhúng /Launch action nguy hiểm)
        String content = new String(bytes, StandardCharsets.ISO_8859_1);
        if (content.contains("/Launch") && (content.contains("/Win") || content.contains(".exe") || content.contains(".bat"))) {
            log.warn("Phát hiện PDF chứa lệnh /Launch độc hại");
            throw new BusinessException(ErrorCode.FILE_UNSAFE, "Phát hiện nội dung không an toàn trong tệp PDF");
        }
    }

    private void validateDocx(byte[] bytes) {
        if (bytes.length < ZIP_MAGIC_BYTES.length) {
            throw new BusinessException(ErrorCode.FILE_INVALID_FORMAT, "Tệp DOCX không hợp lệ");
        }
        byte[] header = Arrays.copyOfRange(bytes, 0, ZIP_MAGIC_BYTES.length);
        if (!Arrays.equals(ZIP_MAGIC_BYTES, header)) {
            throw new BusinessException(ErrorCode.FILE_INVALID_FORMAT, "Chữ ký tệp không phải định dạng DOCX/ZIP hợp lệ");
        }

        // DOCX là tệp ZIP, kiểm tra các tệp bên trong không chứa file thực thi nguy hiểm
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            boolean hasContentTypes = false;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if ("[Content_Types].xml".equalsIgnoreCase(entryName)) {
                    hasContentTypes = true;
                }
                for (String dangerousExt : DANGEROUS_EXTENSIONS) {
                    if (entryName.toLowerCase().endsWith(dangerousExt)) {
                        log.warn("Phát hiện tệp nguy hiểm '{}' bên trong gói DOCX", entryName);
                        throw new BusinessException(ErrorCode.FILE_UNSAFE, "Tệp DOCX chứa thành phần thực thi nguy hiểm");
                    }
                }
                zis.closeEntry();
            }
            if (!hasContentTypes) {
                throw new BusinessException(ErrorCode.FILE_INVALID_FORMAT, "Tệp không phải là văn bản DOCX hợp lệ");
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.warn("Không thể giải nén kiểm tra cấu trúc DOCX: {}", e.getMessage());
            throw new BusinessException(ErrorCode.FILE_INVALID_FORMAT, "Cấu trúc tệp DOCX bị lỗi hoặc không hợp lệ");
        }
    }
}
