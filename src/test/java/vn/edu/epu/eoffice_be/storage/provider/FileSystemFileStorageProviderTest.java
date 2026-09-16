package vn.edu.epu.eoffice_be.storage.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemFileStorageProviderTest {

    @TempDir
    Path tempDir;

    private FileSystemFileStorageProvider provider;

    @BeforeEach
    void setUp() {
        provider = new FileSystemFileStorageProvider(tempDir.toString());
    }

    @Test
    @DisplayName("GetName tra ve filesystem")
    void getName_ReturnsFilesystem() {
        assertEquals("filesystem", provider.getName());
    }

    @Test
    @DisplayName("Luu va lay tep thanh cong day du metadata")
    void saveAndGet_Success() {
        byte[] content = "Hello world storage content".getBytes();
        StoredFile input = StoredFile.builder()
                .originalFileName("report.pdf")
                .contentType("application/pdf")
                .sizeBytes(content.length)
                .content(content)
                .build();

        StoredFile saved = provider.save(input);
        assertNotNull(saved.getId());
        assertEquals("report.pdf", saved.getOriginalFileName());

        StoredFile retrieved = provider.get(saved.getId());
        assertNotNull(retrieved);
        assertEquals(saved.getId(), retrieved.getId());
        assertEquals("report.pdf", retrieved.getOriginalFileName());
        assertEquals("application/pdf", retrieved.getContentType());
        assertEquals(content.length, retrieved.getSizeBytes());
        assertArrayEquals(content, retrieved.getContent());
    }

    @Test
    @DisplayName("Luu tep vuot qua kich thuoc toi da nem FileStorageException")
    void save_FileExceedsLimit_ThrowsException() {
        FileSystemFileStorageProvider limitedProvider = new FileSystemFileStorageProvider(tempDir.toString(), 10L);
        byte[] content = "Nội dung dài hơn 10 bytes".getBytes();
        StoredFile large = StoredFile.builder()
                .originalFileName("large.pdf")
                .content(content)
                .build();

        assertThrows(FileStorageException.class, () -> limitedProvider.save(large));
    }

    @Test
    @DisplayName("Doc tep vuot qua kich thuoc an toan nem FileStorageException chong OOM")
    void get_FileExceedsLimit_ThrowsException() {
        FileSystemFileStorageProvider defaultProvider = new FileSystemFileStorageProvider(tempDir.toString(), 100000L);
        byte[] content = "12345678901234567890".getBytes(); // 20 bytes
        StoredFile saved = defaultProvider.save(StoredFile.builder()
                .originalFileName("data.bin")
                .content(content)
                .build());

        // Tạo provider với giới hạn đọc chỉ 10 bytes để test kiểm tra kích thước
        FileSystemFileStorageProvider strictlyLimitedProvider = new FileSystemFileStorageProvider(tempDir.toString(), 10L);
        assertThrows(FileStorageException.class, () -> strictlyLimitedProvider.get(saved.getId()));
    }

    @Test
    @DisplayName("Luu tep rong nem ngoai le FileStorageException")
    void save_EmptyContent_ThrowsException() {
        StoredFile empty = StoredFile.builder()
                .originalFileName("empty.pdf")
                .content(new byte[0])
                .build();

        assertThrows(FileStorageException.class, () -> provider.save(empty));
    }

    @Test
    @DisplayName("Lay tep khong ton tai nem ngoai le FileStorageException")
    void get_NonExistentFile_ThrowsException() {
        assertThrows(FileStorageException.class, () -> provider.get("unknown-id-123.pdf"));
    }

    @Test
    @DisplayName("Xoa tep thanh cong")
    void delete_Success() {
        byte[] content = "Deletable content".getBytes();
        StoredFile saved = provider.save(StoredFile.builder()
                .originalFileName("temp.docx")
                .content(content)
                .build());

        assertDoesNotThrow(() -> provider.delete(saved.getId()));
        assertThrows(FileStorageException.class, () -> provider.get(saved.getId()));
    }

    @Test
    @DisplayName("GetAll va deleteMany hoat dong dung khi co du lieu thuc te")
    void getAllAndDeleteMany_WithFiles_Success() {
        assertEquals(List.of(), provider.getAll());

        StoredFile file1 = provider.save(StoredFile.builder()
                .originalFileName("doc1.pdf")
                .contentType("application/pdf")
                .content("Doc 1 content".getBytes())
                .build());

        StoredFile file2 = provider.save(StoredFile.builder()
                .originalFileName("doc2.docx")
                .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .content("Doc 2 content".getBytes())
                .build());

        List<StoredFile> files = provider.getAll();
        assertEquals(2, files.size());
        List<String> ids = files.stream().map(StoredFile::getId).toList();
        assertTrue(ids.contains(file1.getId()));
        assertTrue(ids.contains(file2.getId()));

        provider.deleteMany(List.of(file1.getId(), file2.getId()));

        assertEquals(0, provider.getAll().size());
        assertThrows(FileStorageException.class, () -> provider.get(file1.getId()));
        assertThrows(FileStorageException.class, () -> provider.get(file2.getId()));
    }

    @Test
    @DisplayName("Path traversal trong get va delete bi chan boi FileStorageException")
    void pathTraversal_ThrowsException() {
        assertThrows(FileStorageException.class, () -> provider.get("../../../etc/passwd"));
        assertThrows(FileStorageException.class, () -> provider.get("..\\..\\windows\\win.ini"));
        assertThrows(FileStorageException.class, () -> provider.delete("../../../etc/passwd"));
        assertThrows(FileStorageException.class, () -> provider.delete("..\\..\\windows\\win.ini"));
    }
}
