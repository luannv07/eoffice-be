package vn.edu.epu.eoffice_be.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.config.UploadProperties;
import vn.edu.epu.eoffice_be.storage.dto.StoredFileInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileSystemStorageServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private FileSecurityValidator securityValidator;

    private UploadProperties uploadProperties;
    private FileSystemStorageService storageService;

    @BeforeEach
    void setUp() {
        uploadProperties = new UploadProperties();
        uploadProperties.setDir(tempDir.toString().replace('\\', '/'));
        storageService = new FileSystemStorageService(uploadProperties, securityValidator);
    }

    @Test
    @DisplayName("Luu tep thanh cong va tinh ma bam SHA-256")
    void storeFile_Success() {
        byte[] content = "Test file content for storage".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-document.pdf",
                "application/pdf",
                content
        );

        doNothing().when(securityValidator).validateFile(file);
        when(securityValidator.extractExtension("test-document.pdf")).thenReturn("pdf");

        StoredFileInfo info = storageService.storeFile(file, 100L);

        assertNotNull(info);
        assertEquals("test-document.pdf", info.getOriginalFileName());
        assertEquals("PDF", info.getFileExtension());
        assertEquals("application/pdf", info.getMimeType());
        assertEquals(content.length, info.getFileSizeBytes());
        assertNotNull(info.getSha256Checksum());
        assertNotNull(info.getStorageFileName());
        assertTrue(info.getStorageFileName().endsWith(".pdf"));

        // Kiem tra tep thuc su ton tai
        Resource resource = storageService.loadAsResource(info.getStoragePath(), info.getStorageFileName());
        assertNotNull(resource);
        assertTrue(resource.exists());
    }

    @Test
    @DisplayName("Luu tep DOCX khi MIME type null thi tu nhan dien theo phan mo rong")
    void storeFile_Docx_NullMimeType_DefaultsToDocxMime() {
        byte[] content = "Dummy docx content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.docx",
                null,
                content
        );

        doNothing().when(securityValidator).validateFile(file);
        when(securityValidator.extractExtension("sample.docx")).thenReturn("docx");

        StoredFileInfo info = storageService.storeFile(file, 101L);

        assertNotNull(info);
        assertEquals("sample.docx", info.getOriginalFileName());
        assertEquals("DOCX", info.getFileExtension());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", info.getMimeType());
    }

    @Test
    @DisplayName("Tai tep khong ton tai nem ngoai le DOCUMENT_ATTACHMENT_NOT_FOUND")
    void loadAsResource_NonExistentFile_ThrowsNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> storageService.loadAsResource(tempDir.toString(), "non_existent_file.pdf"));
        assertEquals(ErrorCode.DOCUMENT_ATTACHMENT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Xoa tep thanh cong khi tep ton tai")
    void deleteFile_ExistingFile_DeletesSuccessfully() throws IOException {
        Path testFile = tempDir.resolve("to_delete.txt");
        Files.writeString(testFile, "temporary content");
        assertTrue(Files.exists(testFile));

        storageService.deleteFile(tempDir.toString(), "to_delete.txt");
        assertFalse(Files.exists(testFile));
    }

    @Test
    @DisplayName("Xoa tep khong ton tai khong nem ngoai le")
    void deleteFile_NonExistentFile_NoExceptionThrown() {
        storageService.deleteFile(tempDir.toString(), "non_existent.txt");
    }

    @Test
    @DisplayName("Lỗi khi đọc file stream ném ngoại lệ FILE_STORAGE_ERROR")
    void storeFile_IoException_ThrowsFileStorageError() throws IOException {
        org.springframework.web.multipart.MultipartFile badFile = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        when(badFile.getOriginalFilename()).thenReturn("test.pdf");
        when(securityValidator.extractExtension("test.pdf")).thenReturn("pdf");
        when(badFile.getInputStream()).thenThrow(new IOException("Disk error"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> storageService.storeFile(badFile, 102L));
        assertEquals(ErrorCode.FILE_STORAGE_ERROR, ex.getErrorCode());
    }
}
