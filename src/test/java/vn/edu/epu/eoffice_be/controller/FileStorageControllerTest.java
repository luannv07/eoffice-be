package vn.edu.epu.eoffice_be.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.response.StoredFileResponse;
import vn.edu.epu.eoffice_be.entity.StoredFileMetadata;
import vn.edu.epu.eoffice_be.repository.StoredFileMetadataRepository;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageException;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageProvider;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageProviderRegistry;
import vn.edu.epu.eoffice_be.storage.provider.StoredFile;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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

    @Test
    @DisplayName("Upload file thanh cong luu metadata")
    void upload_Success() {
        when(registry.getActiveProvider()).thenReturn(provider);
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
        when(registry.getActiveProvider()).thenReturn(provider);
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
        when(registry.getActiveProvider()).thenReturn(provider);
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
        when(registry.getActiveProvider()).thenReturn(provider);
        BaseResponse<Void> response = controller.delete("file-123", null);

        assertNotNull(response);
        verify(provider).delete("file-123");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "filesystem")
    void deleteMany_resolvesProviderOnceAndChecksAllFilesBeforeDeleting(String selectedProvider) {
        if (selectedProvider == null) when(registry.getActiveProvider()).thenReturn(provider);
        else when(registry.getProvider(selectedProvider)).thenReturn(provider);
        when(provider.getName()).thenReturn("filesystem");
        var ids = List.of("first", "second", "third");

        var response = controller.deleteMany(ids, selectedProvider);

        assertNotNull(response);
        var order = inOrder(registry, provider, documentFiles);
        if (selectedProvider == null) order.verify(registry).getActiveProvider();
        else order.verify(registry).getProvider(selectedProvider);
        order.verify(provider).getName();
        for (String id : ids) order.verify(documentFiles).assertUnboundWrite("filesystem", id);
        order.verify(provider).deleteMany(ids);
        verifyNoMoreInteractions(registry, provider, documentFiles);
    }

    @Test
    void deleteMany_nullListReturnsBadRequestBeforeResolvingProvider() {
        var error = assertThrows(BusinessException.class, () -> controller.deleteMany(null, null));

        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        assertEquals(400, error.getErrorCode().getStatus().value());
        verifyNoInteractions(registry, provider, documentFiles, metadataRepository);
    }

    @Test
    void deleteMany_forbiddenFileStopsDeletionOfTheWholeBatch() {
        when(registry.getActiveProvider()).thenReturn(provider);
        when(provider.getName()).thenReturn("filesystem");
        var ids = List.of("allowed", "protected", "later");
        var denied = new BusinessException(ErrorCode.FORBIDDEN);
        doNothing().when(documentFiles).assertUnboundWrite("filesystem", "allowed");
        doThrow(denied).when(documentFiles).assertUnboundWrite("filesystem", "protected");

        assertEquals(denied, assertThrows(BusinessException.class, () -> controller.deleteMany(ids, null)));

        var order = inOrder(documentFiles);
        order.verify(documentFiles).assertUnboundWrite("filesystem", "allowed");
        order.verify(documentFiles).assertUnboundWrite("filesystem", "protected");
        verifyNoMoreInteractions(documentFiles);
        verify(provider, never()).deleteMany(any());
        verify(registry).getActiveProvider();
        verifyNoMoreInteractions(registry);
    }

    @Test
    void deleteMany_emptyListKeepsExistingProviderBehavior() {
        when(registry.getActiveProvider()).thenReturn(provider);
        var ids = List.<String>of();

        assertNotNull(controller.deleteMany(ids, null));

        verify(provider).deleteMany(ids);
        verifyNoInteractions(documentFiles);
        verify(registry).getActiveProvider();
        verifyNoMoreInteractions(registry);
    }
}
