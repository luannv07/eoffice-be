package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.service.impl.FileStorageServiceImpl;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    private FileStorageServiceImpl fileStorageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageServiceImpl();
        ReflectionTestUtils.setField(fileStorageService, "basePathStr", tempDir.toString());
        fileStorageService.init();
    }

    @Test
    @DisplayName("Lưu file và đọc lại file thành công")
    void storeAndLoadFile_success() {
        byte[] content = "Hello World PDF Content".getBytes();
        String storedPath = fileStorageService.storeFile(content, "test.pdf", "drafts");

        assertNotNull(storedPath);
        assertTrue(storedPath.startsWith("drafts/"));

        byte[] loaded = fileStorageService.loadFileBytes(storedPath);
        assertArrayEquals(content, loaded);
    }

    @Test
    @DisplayName("Lưu file an toàn không bị NPE khi originalFilename là chuỗi rỗng hoặc dấu gạch chéo")
    void storeFile_emptyOrSlashOriginalFilename_shouldNotThrowNPE() {
        byte[] content = "Safe Content".getBytes();

        // Chuỗi rỗng ""
        String pathEmpty = fileStorageService.storeFile(content, "", "drafts");
        assertNotNull(pathEmpty);
        assertTrue(pathEmpty.endsWith("_file.pdf"));

        // Dấu gạch chéo "/"
        String pathSlash = fileStorageService.storeFile(content, "/", "drafts");
        assertNotNull(pathSlash);
        assertTrue(pathSlash.endsWith("_file.pdf"));

        // Dấu gạch chéo ngược "\"
        String pathBackslash = fileStorageService.storeFile(content, "\\", "drafts");
        assertNotNull(pathBackslash);
        assertTrue(pathBackslash.endsWith("_file.pdf"));

        // Null
        String pathNull = fileStorageService.storeFile(content, null, "drafts");
        assertNotNull(pathNull);
        assertTrue(pathNull.endsWith("_file.pdf"));
    }

    @Test
    @DisplayName("Chống tấn công Path Traversal khi lưu file")
    void storeFile_pathTraversalAttack_shouldThrowStorageError() {
        byte[] content = "Attack Content".getBytes();
        BusinessException ex = assertThrows(BusinessException.class, () ->
                fileStorageService.storeFile(content, "hack.pdf", "../outside"));
        assertEquals(ErrorCode.STORAGE_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("Ném lỗi BAD_REQUEST khi content rỗng hoặc null")
    void storeFile_emptyContent_shouldThrowBadRequest() {
        assertThrows(BusinessException.class, () ->
                fileStorageService.storeFile(null, "empty.pdf", "drafts"));
        assertThrows(BusinessException.class, () ->
                fileStorageService.storeFile(new byte[0], "empty.pdf", "drafts"));
    }

    @Test
    @DisplayName("Chống tấn công Path Traversal khi đọc file")
    void loadFile_pathTraversalAttack_shouldThrowStorageError() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                fileStorageService.loadFileBytes("../secret.txt"));
        assertEquals(ErrorCode.STORAGE_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("Ném lỗi DOCUMENT_FILE_MISSING khi file không tồn tại")
    void loadFile_fileNotFound_shouldThrowDocumentFileMissing() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                fileStorageService.loadFileBytes("non-existing.pdf"));
        assertEquals(ErrorCode.DOCUMENT_FILE_MISSING, ex.getErrorCode());
    }

    @Test
    @DisplayName("Tính toán mã băm SHA-256 chính xác")
    void computeSha256_success() {
        byte[] content = "Test Content For SHA256".getBytes();
        String hash = fileStorageService.computeSha256(content);
        assertNotNull(hash);
        assertEquals(64, hash.length());

        assertNull(fileStorageService.computeSha256(null));
    }
}