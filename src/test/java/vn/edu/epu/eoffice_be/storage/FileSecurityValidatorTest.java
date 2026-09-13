package vn.edu.epu.eoffice_be.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.config.UploadProperties;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileSecurityValidatorTest {

    private FileSecurityValidator validator;
    private UploadProperties properties;

    @BeforeEach
    void setUp() {
        properties = new UploadProperties();
        properties.setMaxSizeBytes(10 * 1024 * 1024); // 10MB
        properties.setAllowedExtensions(List.of("docx", "pdf", "DOCX", "PDF"));
        validator = new FileSecurityValidator(properties);
    }

    @Test
    @DisplayName("Tệp PDF hợp lệ với magic bytes %PDF- phải vượt qua kiểm tra")
    void validateFile_ValidPdf_ShouldPass() {
        byte[] validPdfBytes = "%PDF-1.7 header and dummy content".getBytes(StandardCharsets.ISO_8859_1);
        MockMultipartFile file = new MockMultipartFile("file", "document.pdf", "application/pdf", validPdfBytes);

        assertDoesNotThrow(() -> validator.validateFile(file));
    }

    @Test
    @DisplayName("Tệp DOCX hợp lệ với cấu trúc ZIP và [Content_Types].xml phải vượt qua kiểm tra")
    void validateFile_ValidDocx_ShouldPass() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("[Content_Types].xml");
            zos.putNextEntry(entry);
            zos.write("<Types></Types>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                baos.toByteArray());

        assertDoesNotThrow(() -> validator.validateFile(file));
    }

    @Test
    @DisplayName("Tệp không đúng định dạng (.exe) bị từ chối với FILE_INVALID_FORMAT")
    void validateFile_DisallowedExtension_ShouldThrowInvalidFormat() {
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream", new byte[]{1, 2, 3});

        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validateFile(file));
        assertEquals(ErrorCode.FILE_INVALID_FORMAT, ex.getErrorCode());
    }

    @Test
    @DisplayName("Tệp mang đuôi .pdf nhưng nội dung giả mạo bị từ chối với FILE_INVALID_FORMAT")
    void validateFile_FakePdfWithoutMagicBytes_ShouldThrowInvalidFormat() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a pdf header".getBytes(StandardCharsets.UTF_8));

        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validateFile(file));
        assertEquals(ErrorCode.FILE_INVALID_FORMAT, ex.getErrorCode());
    }

    @Test
    @DisplayName("Tên tệp chứa ký tự Path Traversal bị từ chối với FILE_UNSAFE")
    void validateFile_PathTraversalInFilename_ShouldThrowFileUnsafe() {
        MockMultipartFile file = new MockMultipartFile("file", "../../etc/passwd.pdf", "application/pdf", "%PDF-1.5".getBytes(StandardCharsets.UTF_8));

        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validateFile(file));
        assertEquals(ErrorCode.FILE_UNSAFE, ex.getErrorCode());
    }

    @Test
    @DisplayName("Tệp vượt quá kích thước tối đa bị từ chối với FILE_SIZE_EXCEEDED")
    void validateFile_FileSizeExceeded_ShouldThrowFileSizeExceeded() {
        properties.setMaxSizeBytes(100); // 100 bytes limit
        byte[] largeBytes = new byte[200];
        System.arraycopy("%PDF-".getBytes(StandardCharsets.ISO_8859_1), 0, largeBytes, 0, 5);
        MockMultipartFile file = new MockMultipartFile("file", "large.pdf", "application/pdf", largeBytes);

        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validateFile(file));
        assertEquals(ErrorCode.FILE_SIZE_EXCEEDED, ex.getErrorCode());
    }
}
