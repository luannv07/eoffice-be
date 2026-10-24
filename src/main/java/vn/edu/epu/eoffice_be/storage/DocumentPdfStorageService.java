package vn.edu.epu.eoffice_be.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.config.UploadProperties;
import vn.edu.epu.eoffice_be.entity.StoredFileMetadata;
import vn.edu.epu.eoffice_be.repository.StoredFileMetadataRepository;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageProvider;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageProviderRegistry;
import vn.edu.epu.eoffice_be.storage.provider.StoredFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/** Stores immutable signed PDF versions; does not perform signing or authorize document access. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentPdfStorageService {
    private final FileStorageProviderRegistry providerRegistry;
    private final StoredFileMetadataRepository metadataRepository;
    private final vn.edu.epu.eoffice_be.service.FileStorageService legacyFileStorage;
    private final UploadProperties uploadProperties;

    @Transactional
    public String storeSignedPdf(byte[] pdfBytes, String filename, Long documentId) {
        validateInput(pdfBytes, filename, documentId);
        String checksum = checksum(pdfBytes);
        FileStorageProvider provider = activeProvider(documentId);
        StoredFile uploaded;
        try {
            uploaded = provider.saveForDocument(StoredFile.builder()
                    .originalFileName(filename).contentType("application/pdf")
                    .sizeBytes(pdfBytes.length).content(pdfBytes)
                    .metadata(Map.of("documentId", documentId.toString())).build());
        } catch (RuntimeException e) {
            throw failure("upload", documentId, null, e, ErrorCode.DOCUMENT_PDF_STORAGE_UNAVAILABLE);
        }
        if (uploaded == null || uploaded.getId() == null || uploaded.getId().isBlank()) {
            log.error("PDF upload returned no usable receipt: documentId={}", documentId);
            throw new BusinessException(ErrorCode.DOCUMENT_PDF_STORAGE_FAILED);
        }

        // Retain identifiers only, not the potentially large PDF, until transaction completion.
        StoredFile receipt = StoredFile.builder().id(uploaded.getId())
                .metadata(uploaded.getMetadata() == null ? Map.of() : Map.copyOf(uploaded.getMetadata()))
                .build();
        boolean registered = registerRollbackCleanup(provider, receipt, documentId);
        try {
            StoredFileMetadata metadata = metadataRepository
                    .findByProviderAndFileId(provider.getName(), receipt.getId()).orElse(null);
            if (metadata == null) {
                Map<String, String> details = new HashMap<>(receipt.getMetadata());
                details.put("documentId", documentId.toString());
                metadata = StoredFileMetadata.builder()
                        .provider(provider.getName()).fileId(receipt.getId())
                        .originalFileName(filename).contentType("application/pdf")
                        .sizeBytes((long) pdfBytes.length).sha256Checksum(checksum)
                        .storagePath(details.get("storagePath")).metadata(details).build();
            } else {
                // Telegram may return the same file_id for another message. Keep the
                // existing message receipt so old references are never overwritten.
                if (metadata.getSha256Checksum() != null
                        && !metadata.getSha256Checksum().equals(checksum)) {
                    throw new BusinessException(ErrorCode.DOCUMENT_PDF_STORAGE_FAILED);
                }
                metadata.setSha256Checksum(checksum);
            }
            StoredFileMetadata saved = metadataRepository.saveAndFlush(metadata);
            if (saved == null || saved.getId() == null) {
                throw new BusinessException(ErrorCode.DOCUMENT_PDF_STORAGE_FAILED);
            }
            return DocumentPdfReference.format(saved.getId());
        } catch (RuntimeException e) {
            if (!registered) discardQuietly(provider, receipt, documentId);
            throw failure("persist-metadata", documentId, null, e, ErrorCode.DOCUMENT_PDF_STORAGE_FAILED);
        }
    }

    @Transactional(readOnly = true)
    public byte[] readPdf(String reference) {
        return read(reference).content();
    }

    @Transactional(readOnly = true)
    public Resource readAsResource(String reference) {
        PdfContent pdf = read(reference);
        return new ByteArrayResource(pdf.content()) {
            @Override
            public String getFilename() {
                return pdf.filename();
            }
        };
    }

    private PdfContent read(String reference) {
        if (reference == null || reference.isBlank()) {
            log.warn("No PDF reference available for reading");
            throw new BusinessException(ErrorCode.DOCUMENT_PDF_NOT_FOUND);
        }
        if (!DocumentPdfReference.isStorageReference(reference)) {
            return readLegacy(reference);
        }
        long id = DocumentPdfReference.parseMetadataId(reference);
        StoredFileMetadata metadata;
        try {
            metadata = metadataRepository.findById(id).orElse(null);
        } catch (DataAccessException e) {
            throw failure("read-metadata", null, id, e, ErrorCode.DOCUMENT_PDF_STORAGE_FAILED);
        }
        if (metadata == null) {
            log.warn("PDF metadata not found: metadataId={}", id);
            throw new BusinessException(ErrorCode.DOCUMENT_PDF_NOT_FOUND);
        }
        if (metadata.getProvider() == null || metadata.getProvider().isBlank()
                || metadata.getFileId() == null || metadata.getFileId().isBlank()) {
            log.error("PDF metadata has missing provider/file ID: metadataId={}", id);
            throw new BusinessException(ErrorCode.DOCUMENT_PDF_REFERENCE_INVALID);
        }
        if (metadata.getSizeBytes() != null) checkSize(metadata.getSizeBytes());
        StoredFile file;
        try {
            // Use the recorded provider, even if the active provider has changed.
            file = providerRegistry.getProvider(metadata.getProvider()).get(metadata.getFileId());
        } catch (RuntimeException e) {
            throw failure("download", null, id, e, ErrorCode.DOCUMENT_PDF_STORAGE_UNAVAILABLE);
        }
        if (file == null || file.getContent() == null || file.getContent().length == 0) {
            log.warn("PDF provider returned no content: metadataId={}", id);
            throw new BusinessException(ErrorCode.DOCUMENT_PDF_NOT_FOUND);
        }
        checkSize(file.getContent().length);
        return new PdfContent(file.getContent(),
                metadata.getOriginalFileName() == null ? "document.pdf" : metadata.getOriginalFileName());
    }

    private PdfContent readLegacy(String reference) {
        try {
            byte[] content = legacyFileStorage.loadFileBytes(reference);
            if (content == null || content.length == 0) {
                throw new BusinessException(ErrorCode.DOCUMENT_PDF_NOT_FOUND);
            }
            checkSize(content.length);
            return new PdfContent(content, "document.pdf");
        } catch (BusinessException e) {
            ErrorCode mapped = switch (e.getErrorCode()) {
                case DOCUMENT_FILE_MISSING, DOCUMENT_PRIMARY_FILE_MISSING, DOCUMENT_PDF_NOT_FOUND ->
                        ErrorCode.DOCUMENT_PDF_NOT_FOUND;
                case FILE_SIZE_EXCEEDED -> ErrorCode.FILE_SIZE_EXCEEDED;
                default -> ErrorCode.DOCUMENT_PDF_STORAGE_UNAVAILABLE;
            };
            throw failure("read-legacy", null, null, e, mapped);
        } catch (RuntimeException e) {
            throw failure("read-legacy", null, null, e, ErrorCode.DOCUMENT_PDF_STORAGE_UNAVAILABLE);
        }
    }

    private void validateInput(byte[] pdf, String filename, Long documentId) {
        if (pdf == null || pdf.length == 0 || documentId == null || documentId <= 0
                || filename == null || filename.isBlank() || filename.length() > 512
                || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")
                || filename.indexOf('/') >= 0 || filename.indexOf('\\') >= 0
                || filename.indexOf('\r') >= 0 || filename.indexOf('\n') >= 0
                || filename.indexOf('\0') >= 0) {
            log.error("Invalid generated PDF input: documentId={}", documentId);
            throw new BusinessException(ErrorCode.DOCUMENT_PDF_CONTENT_INVALID);
        }
        checkSize(pdf.length);
    }

    private void checkSize(long size) {
        if (size > uploadProperties.getMaxSizeBytes()) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
    }

    private FileStorageProvider activeProvider(Long documentId) {
        try {
            return providerRegistry.getActiveProvider();
        } catch (RuntimeException e) {
            throw failure("resolve-provider", documentId, null, e, ErrorCode.DOCUMENT_PDF_STORAGE_UNAVAILABLE);
        }
    }

    private String checksum(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw failure("checksum", null, null, e, ErrorCode.DOCUMENT_PDF_STORAGE_FAILED);
        }
    }

    private boolean registerRollbackCleanup(FileStorageProvider provider, StoredFile receipt, Long documentId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return false;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) discardQuietly(provider, receipt, documentId);
            }
        });
        return true;
    }

    private void discardQuietly(FileStorageProvider provider, StoredFile receipt, Long documentId) {
        try {
            provider.discardUpload(receipt);
        } catch (RuntimeException e) {
            failure("discard-upload", documentId, null, e, ErrorCode.DOCUMENT_PDF_STORAGE_FAILED);
        }
    }

    private BusinessException failure(String operation, Long documentId, Long metadataId,
                                      Exception cause, ErrorCode code) {
        // Never log raw provider exception messages/URLs: Telegram URLs contain the bot token.
        Throwable root = cause;
        for (int i = 0; i < 10 && root.getCause() != null && root.getCause() != root; i++) {
            root = root.getCause();
        }
        log.error("PDF storage failure: operation={}, documentId={}, metadataId={}, type={}, rootType={}, frames={}",
                operation, documentId, metadataId, cause.getClass().getName(),
                root.getClass().getName(), Arrays.toString(root.getStackTrace()));
        return new BusinessException(code);
    }

    private record PdfContent(byte[] content, String filename) { }
}
