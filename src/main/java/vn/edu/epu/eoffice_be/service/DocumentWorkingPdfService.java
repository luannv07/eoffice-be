package vn.edu.epu.eoffice_be.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentAttachment;
import vn.edu.epu.eoffice_be.repository.DocumentAttachmentRepository;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;

/** Resolves only the current working PDF, never a frozen or superseded attachment. */
@Service
@RequiredArgsConstructor
public class DocumentWorkingPdfService {
    private final DocumentPdfStorageService pdfStorage;
    private final DocumentAttachmentRepository attachments;
    private final vn.edu.epu.eoffice_be.storage.FileStorageService attachmentStorage;

    public byte[] read(Document document) {
        if (document.getPrimaryFilePath() != null && !document.getPrimaryFilePath().isBlank()) {
            return pdfStorage.readPdf(document.getPrimaryFilePath());
        }
        var mains = attachments.findByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(document.getId());
        if (mains.isEmpty()) throw new BusinessException(ErrorCode.DOCUMENT_PRIMARY_FILE_MISSING);
        if (mains.size() != 1) throw new BusinessException(ErrorCode.BAD_REQUEST, "Văn bản phải có đúng một tệp chính");
        DocumentAttachment main = mains.get(0);
        if (!"PDF".equalsIgnoreCase(main.getFileExtension())
                && (main.getOriginalFileName() == null || !main.getOriginalFileName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Ký văn bản yêu cầu tệp chính định dạng PDF");
        }
        try {
            var resource = attachmentStorage.loadAsResource(main.getStoragePath(), main.getStorageFileName());
            if (resource == null || !resource.exists() || !resource.isReadable()) {
                throw new BusinessException(ErrorCode.STORAGE_ERROR, "Không thể đọc tệp PDF chính");
            }
            try (var input = resource.getInputStream()) {
                byte[] bytes = input.readAllBytes();
                if (bytes.length == 0) throw new BusinessException(ErrorCode.DOCUMENT_PRIMARY_FILE_MISSING);
                return bytes;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.STORAGE_ERROR, "Không thể đọc tệp PDF chính");
        }
    }
}
