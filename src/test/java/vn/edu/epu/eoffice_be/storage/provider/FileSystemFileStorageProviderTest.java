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
    @DisplayName("Luu va lay tep thanh cong")
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
        assertArrayEquals(content, retrieved.getContent());
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
    @DisplayName("GetAll tra ve danh sach rong va deleteMany khong loi")
    void getAllAndDeleteMany() {
        assertEquals(List.of(), provider.getAll());
        assertDoesNotThrow(() -> provider.deleteMany(List.of("id1", "id2")));
    }
}
