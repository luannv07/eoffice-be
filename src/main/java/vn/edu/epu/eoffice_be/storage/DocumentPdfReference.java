package vn.edu.epu.eoffice_be.storage;

import lombok.extern.slf4j.Slf4j;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;

/**
 * Encodes references to file_storage_metadata without exposing provider credentials.
 * Legacy paths such as drafts/example.pdf remain distinct from storage references.
 */
@Slf4j
public final class DocumentPdfReference {
    private static final String SCHEME = "storage:";
    private static final String PREFIX = SCHEME + "//";

    private DocumentPdfReference() { }

    public static String format(long metadataId) {
        if (metadataId <= 0) {
            log.warn("Invalid generated PDF metadata ID: {}", metadataId);
            throw new BusinessException(ErrorCode.DOCUMENT_PDF_REFERENCE_INVALID);
        }
        return PREFIX + metadataId;
    }

    /**
     * Recognizes the reserved scheme, including malformed references.
     * A malformed storage reference must be rejected by parseMetadataId rather
     * than treated as a legacy local path.
     */
    public static boolean isStorageReference(String reference) {
        return reference != null && reference.startsWith(SCHEME);
    }

    public static long parseMetadataId(String reference) {
        if (reference == null || !reference.startsWith(PREFIX)) {
            log.warn("PDF reference is missing or has an invalid prefix");
            throw new BusinessException(ErrorCode.DOCUMENT_PDF_REFERENCE_INVALID);
        }
        String value = reference.substring(PREFIX.length());
        if (!value.matches("[1-9][0-9]*")) {
            log.warn("PDF reference has a non-canonical metadata ID");
            throw new BusinessException(ErrorCode.DOCUMENT_PDF_REFERENCE_INVALID);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("PDF reference metadata ID exceeds the long range");
            throw new BusinessException(ErrorCode.DOCUMENT_PDF_REFERENCE_INVALID);
        }
    }
}
