package vn.edu.epu.eoffice_be.storage;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

class DocumentPdfReferenceTest {
    @ParameterizedTest
    @ValueSource(longs = {1, 35001, Long.MAX_VALUE})
    void roundTripPreservesMetadataId(long id) {
        String reference = DocumentPdfReference.format(id);

        assertEquals("storage://" + id, reference);
        assertTrue(DocumentPdfReference.isStorageReference(reference));
        assertEquals(id, DocumentPdfReference.parseMetadataId(reference));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void formatRejectsNonPositiveIds(long id) {
        BusinessException error = assertThrows(BusinessException.class,
                () -> DocumentPdfReference.format(id));
        assertEquals(ErrorCode.DOCUMENT_PDF_REFERENCE_INVALID, error.getErrorCode());
        assertEquals(ErrorCode.DOCUMENT_PDF_REFERENCE_INVALID.getMessage(), error.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "drafts/example.pdf", "official/example.pdf",
            "/app/uploads/drafts/example.pdf", "C:\\uploads\\example.pdf"
    })
    void legacyPathsAreNotStorageReferences(String reference) {
        assertFalse(DocumentPdfReference.isStorageReference(reference));
        BusinessException error = assertThrows(BusinessException.class,
                () -> DocumentPdfReference.parseMetadataId(reference));
        assertEquals(ErrorCode.DOCUMENT_PDF_REFERENCE_INVALID, error.getErrorCode());
        assertEquals(ErrorCode.DOCUMENT_PDF_REFERENCE_INVALID.getMessage(), error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "storage:", "storage:/1", "storage://", "storage://0", "storage://-1",
            "storage://+1", "storage://01", "storage://1.5", "storage://abc",
            "storage://1/file.pdf", "storage://1?token=value", "storage://1#fragment",
            "storage://../1", "storage:// 1", "storage://1 ", "storage://１",
            "storage://9223372036854775808"
    })
    void reservedButInvalidReferencesCannotBecomeLocalPaths(String reference) {
        assertTrue(DocumentPdfReference.isStorageReference(reference));
        BusinessException error = assertThrows(BusinessException.class,
                () -> DocumentPdfReference.parseMetadataId(reference));
        assertEquals(ErrorCode.DOCUMENT_PDF_REFERENCE_INVALID, error.getErrorCode());
        assertEquals(ErrorCode.DOCUMENT_PDF_REFERENCE_INVALID.getMessage(), error.getMessage());
    }
}
