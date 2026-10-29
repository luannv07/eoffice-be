package vn.edu.epu.eoffice_be.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.dto.response.StoredFileResponse;
import vn.edu.epu.eoffice_be.entity.StoredFileMetadata;
import vn.edu.epu.eoffice_be.repository.StoredFileMetadataRepository;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageException;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageProvider;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageProviderRegistry;
import vn.edu.epu.eoffice_be.storage.provider.StoredFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileStorageControllerTest {

    @Mock
    private FileStorageProviderRegistry registry;

    @Mock
    private StoredFileMetadataRepository metadataRepository;

    @Mock
    private FileStorageProvider provider;

    @org.mockito.Mock
    private vn.edu.epu.eoffice_be.service.DocumentReadPolicy draftReadPolicy;
    @org.mockito.Mock
    private vn.edu.epu.eoffice_be.service.DraftUnitResolver draftUnitResolver;
    @org.mockito.Mock
    private vn.edu.epu.eoffice_be.service.DocumentFileReadGuard documentFiles;

    @InjectMocks
    private FileStorageController controller;

    @BeforeEach
    void setUp() {
        when(registry.getActiveProvider()).thenReturn(provider);
    }

    @Test
    @DisplayName("Upload file thanh cong luu metadata")
    void upload_Success() {
        when(provider.getName()).thenReturn("filesystem");
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "Hello content".getBytes()
        );

        StoredFile savedFile = StoredFile.builder()
                .id("file-123")
                .originalFileName("report.pdf")
                .contentType("application/pdf")
                .sizeBytes(13)
                .metadata(Map.of("storagePath", "/path/file-123"))
                .build();

        when(provider.save(any(StoredFile.class))).thenReturn(savedFile);

        BaseResponse<StoredFileResponse> response = controller.upload(file, null);

        assertNotNull(response);
        assertEquals("file-123", response.getData().getId());
        verify(metadataRepository).save(any(StoredFileMetadata.class));
    }

    @Test
    @DisplayName("Upload metadata loi thi rollback file va nem FileStorageException")
    void upload_MetadataSaveFails_RollbacksFileAndThrows() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "Hello content".getBytes()
        );

        StoredFile savedFile = StoredFile.builder()
                .id("file-orphan")
                .originalFileName("report.pdf")
                .contentType("application/pdf")
                .sizeBytes(13)
                .build();

        when(provider.getName()).thenReturn("filesystem");
        when(provider.save(any(StoredFile.class))).thenReturn(savedFile);
        when(metadataRepository.save(any(StoredFileMetadata.class)))
                .thenThrow(new RuntimeException("Database down"));

        FileStorageException ex = assertThrows(FileStorageException.class, () -> controller.upload(file, null));
        assertEquals("Lưu siêu dữ liệu tệp thất bại", ex.getMessage());

        verify(provider).delete("file-orphan");
    }

    @Test
    @DisplayName("Download file thanh cong")
    void download_Success() {
        StoredFile storedFile = StoredFile.builder()
                .id("file-123")
                .originalFileName("test.pdf")
                .contentType("application/pdf")
                .content("PDF content".getBytes())
                .build();

        when(provider.get("file-123")).thenReturn(storedFile);

        ResponseEntity<Resource> response = controller.download("file-123", null);

        assertNotNull(response.getBody());
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    @DisplayName("Delete file goi provider delete")
    void delete_Success() {
        BaseResponse<Void> response = controller.delete("file-123", null);

        assertNotNull(response);
        verify(provider).delete("file-123");
    }
}
