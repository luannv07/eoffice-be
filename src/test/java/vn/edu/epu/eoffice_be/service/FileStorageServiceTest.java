package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.service.impl.FileStorageServiceImpl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
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
    @DisplayName("Ném lỗi STORAGE_ERROR khi kích thước file vượt quá ngưỡng an toàn cho phép khi đọc byte[]")
    void loadFileBytes_fileExceedsSizeLimit_shouldThrowStorageError() {
        byte[] content = "Content larger than limit".getBytes();
        String storedPath = fileStorageService.storeFile(content, "large.txt", "drafts");

        // Giới hạn maxFileSizeBytes xuống 10 bytes
        ReflectionTestUtils.setField(fileStorageService, "maxFileSizeBytes", 10L);

        try {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    fileStorageService.loadFileBytes(storedPath));
            assertEquals(ErrorCode.STORAGE_ERROR, ex.getErrorCode());
        } finally {
            // Khôi phục lại giới hạn mặc định
            ReflectionTestUtils.setField(fileStorageService, "maxFileSizeBytes", 20 * 1024 * 1024L);
        }
    }

    @Test
    @DisplayName("Mở luồng InputStream đọc nội dung file thành công")
    void loadFileInputStream_success() throws IOException {
        byte[] content = "Streaming PDF Content".getBytes();
        String storedPath = fileStorageService.storeFile(content, "stream.pdf", "drafts");

        try (InputStream is = fileStorageService.loadFileInputStream(storedPath)) {
            assertNotNull(is);
            byte[] readBytes = is.readAllBytes();
            assertArrayEquals(content, readBytes);
        }
    }

    @Test
    @DisplayName("Tải file dưới dạng Spring Resource thành công")
    void loadFileAsResource_success() throws IOException {
        byte[] content = "Resource PDF Content".getBytes();
        String storedPath = fileStorageService.storeFile(content, "resource.pdf", "drafts");

        Resource resource = fileStorageService.loadFileAsResource(storedPath);
        assertNotNull(resource);
        assertTrue(resource.exists());
        assertTrue(resource.isReadable());

        try (InputStream is = resource.getInputStream()) {
            byte[] readBytes = is.readAllBytes();
            assertArrayEquals(content, readBytes);
        }
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

    @Test
    @DisplayName("Chống tấn công qua symlink trỏ ra ngoài storage root khi lưu file và đọc file")
    void symlinkTraversalAttack_shouldThrowStorageError() throws IOException {
        Path outsideDir = Files.createTempDirectory("outside-dir");
        Path linkDir = tempDir.resolve("link-dir");
        try {
            Path outsideFile = outsideDir.resolve("secret.txt");
            Files.writeString(outsideFile, "SECRET_DATA");

            boolean created = createSymlinkOrJunction(linkDir, outsideDir);
            Assumptions.assumeTrue(created, "Symlink/Junction creation not supported in this environment");

            // 1. Thử lưu file vào thư mục symlink trỏ ra ngoài
            byte[] content = "Malicious Write".getBytes();
            BusinessException storeEx = assertThrows(BusinessException.class, () ->
                    fileStorageService.storeFile(content, "test.pdf", "link-dir"));
            assertEquals(ErrorCode.STORAGE_ERROR, storeEx.getErrorCode());

            // 2. Thử đọc file nằm ngoài root thông qua symlink directory
            BusinessException loadEx = assertThrows(BusinessException.class, () ->
                    fileStorageService.loadFileBytes("link-dir/secret.txt"));
            assertEquals(ErrorCode.STORAGE_ERROR, loadEx.getErrorCode());
        } finally {
            try {
                if (Files.exists(linkDir, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    if (System.getProperty("os.name").toLowerCase().contains("win")) {
                        new ProcessBuilder("cmd.exe", "/c", "rmdir", linkDir.toAbsolutePath().toString()).start().waitFor();
                    } else {
                        Files.deleteIfExists(linkDir);
                    }
                }
                Files.deleteIfExists(outsideDir.resolve("secret.txt"));
                Files.deleteIfExists(outsideDir);
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("Chống tấn công đọc file qua file symlink trỏ ra ngoài storage root")
    void loadFile_fileSymlinkTraversal_shouldThrowStorageError() throws IOException {
        Path outsideFile = Files.createTempFile("outside-file", ".txt");
        Path linkFile = tempDir.resolve("link-file.txt");
        try {
            Files.writeString(outsideFile, "TOP_SECRET");

            boolean created = false;
            try {
                Files.createSymbolicLink(linkFile, outsideFile);
                created = true;
            } catch (Exception ignored) {
            }

            if (created) {
                BusinessException ex = assertThrows(BusinessException.class, () ->
                        fileStorageService.loadFileBytes("link-file.txt"));
                assertEquals(ErrorCode.STORAGE_ERROR, ex.getErrorCode());
            }
        } finally {
            try {
                Files.deleteIfExists(linkFile);
                Files.deleteIfExists(outsideFile);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean createSymlinkOrJunction(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (Exception e) {
            if (System.getProperty("os.name").toLowerCase().contains("win") && Files.isDirectory(target)) {
                try {
                    Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toAbsolutePath().toString(), target.toAbsolutePath().toString())
                            .redirectErrorStream(true)
                            .start();
                    int exitCode = process.waitFor();
                    return exitCode == 0;
                } catch (Exception ignored) {
                }
            }
            return false;
        }
    }
}