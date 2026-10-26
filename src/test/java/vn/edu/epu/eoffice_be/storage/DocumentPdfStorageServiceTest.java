package vn.edu.epu.eoffice_be.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.config.UploadProperties;
import vn.edu.epu.eoffice_be.entity.StoredFileMetadata;
import vn.edu.epu.eoffice_be.repository.StoredFileMetadataRepository;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageException;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageProvider;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageProviderRegistry;
import vn.edu.epu.eoffice_be.storage.provider.StoredFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentPdfStorageServiceTest {
    private static final byte[] PDF = "%PDF-1.7 synthetic signed bytes".getBytes(StandardCharsets.US_ASCII);
    private FileStorageProvider provider;
    private StoredFileMetadataRepository repository;
    private vn.edu.epu.eoffice_be.service.FileStorageService legacy;
    private UploadProperties properties;
    private DocumentPdfStorageService service;
    private FileStorageProviderRegistry registry;
    private StoredFile receipt;

    @BeforeEach
    void setUp() {
        provider = mock(FileStorageProvider.class);
        when(provider.getName()).thenReturn("telegram");
        registry = new FileStorageProviderRegistry(List.of(provider), "telegram");
        repository = mock(StoredFileMetadataRepository.class);
        legacy = mock(vn.edu.epu.eoffice_be.service.FileStorageService.class);
        properties = new UploadProperties();
        service = new DocumentPdfStorageService(registry, repository, legacy, properties);
        receipt = StoredFile.builder().id("telegram-file")
                .metadata(Map.of("messageId", "87", "chatId", "-1000001")).build();
        lenient().when(provider.saveForDocument(any())).thenReturn(receipt);
        lenient().when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            StoredFileMetadata metadata = invocation.getArgument(0);
            metadata.setId(42001L);
            return metadata;
        });
    }

    @Test
    void uploadPersistsExactBytesChecksumAndReceipt() throws Exception {
        String reference = service.storeSignedPdf(PDF, "signed.pdf", 34103L);

        assertEquals("storage://42001", reference);
        var upload = org.mockito.ArgumentCaptor.forClass(StoredFile.class);
        verify(provider).saveForDocument(upload.capture());
        assertSame(PDF, upload.getValue().getContent());
        assertEquals("application/pdf", upload.getValue().getContentType());
        assertEquals("34103", upload.getValue().getMetadata().get("documentId"));
        var saved = org.mockito.ArgumentCaptor.forClass(StoredFileMetadata.class);
        verify(repository).saveAndFlush(saved.capture());
        assertEquals("telegram", saved.getValue().getProvider());
        assertEquals("telegram-file", saved.getValue().getFileId());
        assertEquals("87", saved.getValue().getMetadata().get("messageId"));
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(PDF)),
                saved.getValue().getSha256Checksum());
        verify(provider, never()).save(any());
        verifyNoInteractions(legacy);
    }

    @Test
    void freshServiceReadsUsingRecordedProviderEvenWhenActiveProviderChanges() throws Exception {
        StoredFileMetadata metadata = metadata();
        when(repository.findById(42001L)).thenReturn(Optional.of(metadata));
        when(provider.get("telegram-file")).thenReturn(StoredFile.builder().content(PDF).build());
        FileStorageProviderRegistry changed = new FileStorageProviderRegistry(List.of(provider), "filesystem");
        var restarted = new DocumentPdfStorageService(changed, repository, legacy, properties);

        assertArrayEquals(PDF, restarted.readPdf("storage://42001"));
        var resource = restarted.readAsResource("storage://42001");
        assertEquals("signed.pdf", resource.getFilename());
        assertArrayEquals(PDF, resource.getInputStream().readAllBytes());
        verifyNoInteractions(legacy);
    }

    @Test
    void uploadFailureDoesNotPersistOrFallBackToFilesystem() {
        when(provider.saveForDocument(any())).thenThrow(
                new FileStorageException("https://api.telegram.org/botSECRET/sendDocument"));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.storeSignedPdf(PDF, "signed.pdf", 34103L));
        assertEquals(ErrorCode.DOCUMENT_PDF_STORAGE_UNAVAILABLE, error.getErrorCode());
        assertFalse(error.getMessage().contains("SECRET"));
        assertNull(error.getCause());
        verifyNoInteractions(repository, legacy);
        verify(provider, never()).discardUpload(any());
    }

    @Test
    void metadataFailureImmediatelyDiscardsUploadWhenThereIsNoTransactionSynchronization() {
        doThrow(new DataAccessResourceFailureException("DB offline")).when(repository).saveAndFlush(any());
        assertCode(ErrorCode.DOCUMENT_PDF_STORAGE_FAILED,
                () -> service.storeSignedPdf(PDF, "signed.pdf", 34103L));
        verify(provider).discardUpload(argThat(value ->
                "87".equals(value.getMetadata().get("messageId")) && value.getContent() == null));
    }

    @Test
    void cleanupFailureDoesNotReplaceOriginalError() {
        doThrow(new DataAccessResourceFailureException("DB offline")).when(repository).saveAndFlush(any());
        doThrow(new FileStorageException("cleanup failed")).when(provider).discardUpload(any());
        assertCode(ErrorCode.DOCUMENT_PDF_STORAGE_FAILED,
                () -> service.storeSignedPdf(PDF, "signed.pdf", 34103L));
    }

    @Test
    void laterBusinessRollbackDiscardsNewUpload() {
        transaction().executeWithoutResult(status -> {
            assertEquals("storage://42001", service.storeSignedPdf(PDF, "signed.pdf", 34103L));
            verify(provider, never()).discardUpload(any());
            status.setRollbackOnly();
        });
        verify(provider).discardUpload(any());
        verify(provider, never()).delete(anyString());
    }

    @Test
    void committedTransactionKeepsUpload() {
        transaction().executeWithoutResult(status ->
                service.storeSignedPdf(PDF, "signed.pdf", 34103L));
        verify(provider, never()).discardUpload(any());
    }

    @Test
    void metadataFailureInsideTransactionDiscardsOnceAfterRollback() {
        doThrow(new DataAccessResourceFailureException("DB offline")).when(repository).saveAndFlush(any());
        assertThrows(BusinessException.class, () -> transaction().executeWithoutResult(status ->
                service.storeSignedPdf(PDF, "signed.pdf", 34103L)));
        verify(provider, times(1)).discardUpload(any());
    }

    @Test
    void duplicateTelegramFileKeepsOldMessageReceipt() {
        StoredFileMetadata old = metadata();
        old.setMetadata(Map.of("messageId", "12", "chatId", "-1000001"));
        when(repository.findByProviderAndFileId("telegram", "telegram-file")).thenReturn(Optional.of(old));
        transaction().executeWithoutResult(status -> {
            service.storeSignedPdf(PDF, "signed.pdf", 34103L);
            status.setRollbackOnly();
        });
        assertEquals("12", old.getMetadata().get("messageId"));
        verify(provider).discardUpload(argThat(value ->
                "87".equals(value.getMetadata().get("messageId"))));
    }

    @Test
    void invalidGeneratedInputNeverUploads() {
        for (byte[] invalid : new byte[][]{null, new byte[0]}) {
            assertCode(ErrorCode.DOCUMENT_PDF_CONTENT_INVALID,
                    () -> service.storeSignedPdf(invalid, "signed.pdf", 34103L));
        }
        for (String invalid : new String[]{null, "", "../signed.pdf", "signed.txt", "bad\n.pdf"}) {
            assertCode(ErrorCode.DOCUMENT_PDF_CONTENT_INVALID,
                    () -> service.storeSignedPdf(PDF, invalid, 34103L));
        }
        assertCode(ErrorCode.DOCUMENT_PDF_CONTENT_INVALID,
                () -> service.storeSignedPdf(PDF, "signed.pdf", 0L));
        verify(provider, never()).saveForDocument(any());
    }

    @Test
    void oversizedPdfNeverUploads() {
        properties.setMaxSizeBytes(2);
        assertCode(ErrorCode.FILE_SIZE_EXCEEDED,
                () -> service.storeSignedPdf(PDF, "signed.pdf", 34103L));
        verify(provider, never()).saveForDocument(any());
    }

    @Test
    void malformedReferenceNeverFallsBackToLocalFile() {
        assertCode(ErrorCode.DOCUMENT_PDF_REFERENCE_INVALID, () -> service.readPdf("storage://abc"));
        verifyNoInteractions(repository, legacy);
    }

    @Test
    void missingMetadataReturnsNotFoundWithoutTryingOriginalAttachment() {
        assertCode(ErrorCode.DOCUMENT_PDF_NOT_FOUND, () -> service.readPdf("storage://42001"));
        verify(provider, never()).get(anyString());
        verifyNoInteractions(legacy);
    }

    @Test
    void missingReferenceReturnsNotFound() {
        assertCode(ErrorCode.DOCUMENT_PDF_NOT_FOUND, () -> service.readPdf(null));
        assertCode(ErrorCode.DOCUMENT_PDF_NOT_FOUND, () -> service.readPdf(" "));
        verifyNoInteractions(legacy, repository);
    }

    @Test
    void metadataDatabaseFailureIsNotReportedAsMissingFile() {
        when(repository.findById(42001L)).thenThrow(new DataAccessResourceFailureException("SQL details"));
        assertCode(ErrorCode.DOCUMENT_PDF_STORAGE_FAILED, () -> service.readPdf("storage://42001"));
    }

    @Test
    void providerFailureIsUnavailableAndDoesNotFallBack() {
        when(repository.findById(42001L)).thenReturn(Optional.of(metadata()));
        when(provider.get("telegram-file")).thenThrow(new FileStorageException("token SECRET"));
        assertCode(ErrorCode.DOCUMENT_PDF_STORAGE_UNAVAILABLE, () -> service.readPdf("storage://42001"));
        verifyNoInteractions(legacy);
    }

    @Test
    void emptyProviderResultIsNotFound() {
        when(repository.findById(42001L)).thenReturn(Optional.of(metadata()));
        when(provider.get("telegram-file")).thenReturn(StoredFile.builder().content(new byte[0]).build());
        assertCode(ErrorCode.DOCUMENT_PDF_NOT_FOUND, () -> service.readPdf("storage://42001"));
    }

    @Test
    void oversizedRecordedFileIsRejectedBeforeDownload() {
        StoredFileMetadata metadata = metadata();
        metadata.setSizeBytes(properties.getMaxSizeBytes() + 1);
        when(repository.findById(42001L)).thenReturn(Optional.of(metadata));
        assertCode(ErrorCode.FILE_SIZE_EXCEEDED, () -> service.readPdf("storage://42001"));
        verify(provider, never()).get(anyString());
    }

    @Test
    void legacyFilesStillUseValidatedLegacyReader() {
        when(legacy.loadFileBytes("drafts/old.pdf")).thenReturn(PDF);
        assertArrayEquals(PDF, service.readPdf("drafts/old.pdf"));
        verifyNoInteractions(repository);
    }

    @Test
    void missingLegacyFileReturnsSpecificError() {
        when(legacy.loadFileBytes("drafts/old.pdf"))
                .thenThrow(new BusinessException(ErrorCode.DOCUMENT_FILE_MISSING));
        assertCode(ErrorCode.DOCUMENT_PDF_NOT_FOUND, () -> service.readPdf("drafts/old.pdf"));
    }

    private StoredFileMetadata metadata() {
        return StoredFileMetadata.builder().id(42001L).provider("telegram").fileId("telegram-file")
                .originalFileName("signed.pdf").sizeBytes((long) PDF.length).build();
    }

    private void assertCode(ErrorCode expected, org.junit.jupiter.api.function.Executable action) {
        BusinessException exception = assertThrows(BusinessException.class, action);
        assertEquals(expected, exception.getErrorCode());
        assertEquals(expected.getMessage(), exception.getMessage());
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
            @Override protected void doCommit(DefaultTransactionStatus status) { }
            @Override protected void doRollback(DefaultTransactionStatus status) { }
        });
    }
}
