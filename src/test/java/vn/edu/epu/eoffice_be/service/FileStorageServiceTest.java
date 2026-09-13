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

import java.io.ByteArrayInputStream;
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
    @DisplayName("Tính toán mã băm SHA-256 chính xác từ byte[]")
    void computeSha256_success() {
        byte[] content = "Test Content For SHA256".getBytes();
        String hash = fileStorageService.computeSha256(content);
        assertNotNull(hash);
        assertEquals(64, hash.length());

        assertNull(fileStorageService.computeSha256((byte[]) null));
    }

    @Test
    @DisplayName("Tính toán mã băm SHA-256 từ InputStream khớp với byte[] và hỗ trợ streaming")
    void computeSha256_withInputStream_matchesByteArray() {
        byte[] content = "Test Content For Streaming SHA256".getBytes();
        String byteHash = fileStorageService.computeSha256(content);
        String streamHash = fileStorageService.computeSha256(new ByteArrayInputStream(content));

        assertNotNull(streamHash);
        assertEquals(64, streamHash.length());
        assertEquals(byteHash, streamHash);

        assertNull(fileStorageService.computeSha256((InputStream) null));
    }

    @Test
    @DisplayName("Tính toán mã băm SHA-256 từ InputStream gặp lỗi I/O ném ra STORAGE_ERROR")
    void computeSha256_withBrokenStream_shouldThrowStorageError() {
        InputStream brokenStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Simulated I/O failure during stream hashing");
            }
        };

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileStorageService.computeSha256(brokenStream));
        assertEquals(ErrorCode.STORAGE_ERROR, ex.getErrorCode());
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
                // Ignore cleanup errors in temporary test directories
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
                // Ignore symlink creation failure when runner OS permissions do not permit symlinks
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
                // Ignore cleanup errors for temporary files
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
                    // Ignore junction creation failure
                }
            }
            return false;
        }
    }

    @Test
    @DisplayName("Lưu file khi subDirectory là null hoặc rỗng")
    void storeFile_emptySubDirectory_shouldSaveAtRoot() {
        byte[] content = "Root Content".getBytes();
        String storedPathNull = fileStorageService.storeFile(content, "root.pdf", null);
        assertNotNull(storedPathNull);
        assertFalse(storedPathNull.contains("/"));
        byte[] readNull = fileStorageService.loadFileBytes(storedPathNull);
        assertArrayEquals(content, readNull);

        String storedPathBlank = fileStorageService.storeFile(content, "root2.pdf", "   ");
        assertNotNull(storedPathBlank);
        assertFalse(storedPathBlank.contains("/"));
    }

    @Test
    @DisplayName("Ném lỗi DOCUMENT_FILE_MISSING khi relativePath là null hoặc rỗng")
    void loadFile_nullOrBlankPath_shouldThrowDocumentFileMissing() {
        assertThrows(BusinessException.class, () -> fileStorageService.loadFileBytes(null));
        assertThrows(BusinessException.class, () -> fileStorageService.loadFileBytes("   "));
        assertThrows(BusinessException.class, () -> fileStorageService.loadFileInputStream(null));
        assertThrows(BusinessException.class, () -> fileStorageService.loadFileAsResource(null));
        assertNull(fileStorageService.computeSha256((byte[]) null));
    }

    @Test
    @DisplayName("Nạp file dưới dạng Spring Resource không tồn tại ném ra DOCUMENT_FILE_MISSING")
    void loadFileAsResource_fileNotFound_shouldThrowDocumentFileMissing() {
        assertThrows(BusinessException.class, () -> fileStorageService.loadFileAsResource("non-existent.pdf"));
    }

    @Test
    @DisplayName("Xóa file đã lưu thành công và trả về false khi file không tồn tại hoặc path null/blank")
    void deleteFile_testCases() {
        byte[] content = "Content to delete".getBytes();
        String storedPath = fileStorageService.storeFile(content, "to_delete.pdf", "drafts");
        assertNotNull(storedPath);

        // Xóa file tồn tại
        boolean deleted = fileStorageService.deleteFile(storedPath);
        assertTrue(deleted);

        // Xóa lại file vừa bị xóa -> false
        assertFalse(fileStorageService.deleteFile(storedPath));

        // Xóa với null hoặc blank -> false
        assertFalse(fileStorageService.deleteFile(null));
        assertFalse(fileStorageService.deleteFile("   "));
    }

    @Test
    @DisplayName("subDirectory có khoảng trắng đầu/cuối phải được trim và path trả về khớp với file thực tế")
    void storeFile_subDirectoryWithSpaces_shouldTrimAndLoadSuccessfully() {
        byte[] content = "Trimmed subdir content".getBytes();
        String storedPath = fileStorageService.storeFile(content, "doc.pdf", "  drafts  ");

        assertNotNull(storedPath);
        assertTrue(storedPath.startsWith("drafts/"), "Path phải bắt đầu bằng 'drafts/' sau khi trim");
        assertFalse(storedPath.contains("  "), "Path không được chứa khoảng trắng thừa");

        // Đọc lại file phải thành công — đảm bảo relativePath khớp với vị trí file thực tế
        byte[] loaded = fileStorageService.loadFileBytes(storedPath);
        assertArrayEquals(content, loaded);
    }

    @Test
    @DisplayName("subDirectory chứa backslash phải được chuyển thành forward slash và path trả về nhất quán")
    void storeFile_subDirectoryWithBackslash_shouldNormalizeToForwardSlash() {
        byte[] content = "Backslash subdir content".getBytes();
        String storedPath = fileStorageService.storeFile(content, "doc.pdf", "docs\\2026");

        assertNotNull(storedPath);
        assertFalse(storedPath.contains("\\"), "Path trả về không được chứa backslash");
        assertTrue(storedPath.startsWith("docs/2026/"), "Path phải dùng forward slash");

        // Đọc lại file phải thành công — đảm bảo relativePath khớp với vị trí file thực tế
        byte[] loaded = fileStorageService.loadFileBytes(storedPath);
        assertArrayEquals(content, loaded);
    }
}