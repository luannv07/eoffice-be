package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.repository.DocumentAttachmentRepository;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentWorkingPdfServiceTest {
    @Mock DocumentPdfStorageService storage;
    @Mock DocumentAttachmentRepository attachments;
    @Mock vn.edu.epu.eoffice_be.storage.FileStorageService attachmentStorage;
    @InjectMocks DocumentWorkingPdfService service;

    @Test
    void rejectsNullDocumentBeforeAccessingStorage() {
        BusinessException failure = assertThrows(BusinessException.class, () -> service.read(null));
        assertEquals(ErrorCode.BAD_REQUEST, failure.getErrorCode());
        assertEquals("Văn bản không được để trống", failure.getMessage());
        verifyNoInteractions(storage, attachments, attachmentStorage);
    }

    @Test
    void rejectsNullPrimaryPdfWithoutFallingBackToAttachments() {
        Document document = Document.builder().id(1L).primaryFilePath("storage://signed").build();
        when(storage.readPdf("storage://signed")).thenReturn(null);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.read(document));

        assertEquals(ErrorCode.DOCUMENT_PRIMARY_FILE_MISSING, failure.getErrorCode());
        verifyNoInteractions(attachments, attachmentStorage);
    }

    @Test
    void rejectsEmptyPrimaryPdfWithoutFallingBackToAttachments() {
        Document document = Document.builder().id(1L).primaryFilePath("storage://signed").build();
        when(storage.readPdf("storage://signed")).thenReturn(new byte[0]);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.read(document));

        assertEquals(ErrorCode.DOCUMENT_PRIMARY_FILE_MISSING, failure.getErrorCode());
        verifyNoInteractions(attachments, attachmentStorage);
    }

    @Test
    void wrapsUnexpectedPrimaryStorageFailureWithoutFallingBackToAttachments() {
        Document document = Document.builder().id(1L).primaryFilePath("storage://signed").build();
        when(storage.readPdf("storage://signed")).thenThrow(new IllegalStateException("Storage unavailable"));

        BusinessException failure = assertThrows(BusinessException.class, () -> service.read(document));

        assertEquals(ErrorCode.STORAGE_ERROR, failure.getErrorCode());
        assertEquals("Không thể đọc tệp PDF chính", failure.getMessage());
        verifyNoInteractions(attachments, attachmentStorage);
    }

    @Test
    void readsLatestSignedReferenceWithoutFallingBackToAttachments() {
        Document document = Document.builder().id(1L).primaryFilePath("storage://signed").build();
        byte[] pdf = {1, 2};
        when(storage.readPdf("storage://signed")).thenReturn(pdf);
        assertSame(pdf, service.read(document));
        verifyNoInteractions(attachments, attachmentStorage);
    }

    @Test
    void missingSignedVersionCannotFallBackToUnsignedOriginal() {
        Document document = Document.builder().id(1L).primaryFilePath("storage://missing").build();
        BusinessException failure = new BusinessException(ErrorCode.DOCUMENT_PDF_NOT_FOUND);
        when(storage.readPdf("storage://missing")).thenThrow(failure);
        assertSame(failure, assertThrows(BusinessException.class, () -> service.read(document)));
        verifyNoInteractions(attachments, attachmentStorage);
    }

    @Test
    void frozenAttachmentsAreNotUsedWhenThereIsNoMainPdf() {
        Document document = Document.builder().id(1L).build();
        when(attachments.findByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(1L)).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> service.read(document));
        verifyNoInteractions(attachmentStorage);
        verify(attachments, never()).findByDocumentIdAndDeleteFlgFalse(1L);
    }

    @Test
    void readsExplicitMainPdf() {
        Document document = Document.builder().id(1L).build();
        var main = DocumentAttachment.builder().fileExtension("pdf").storagePath("main").storageFileName("new.pdf").build();
        when(attachments.findByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(1L)).thenReturn(List.of(main));
        byte[] pdf = {1, 2, 3};
        when(attachmentStorage.loadAsResource("main", "new.pdf")).thenReturn(new ByteArrayResource(pdf));
        assertArrayEquals(pdf, service.read(document));
    }

    @Test
    void rejectsMainFileThatIsNotPdf() {
        Document document = Document.builder().id(1L).build();
        var main = DocumentAttachment.builder().fileExtension("docx").originalFileName("main.docx").build();
        when(attachments.findByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(1L)).thenReturn(List.of(main));
        assertThrows(BusinessException.class, () -> service.read(document));
        verifyNoInteractions(attachmentStorage);
    }
}
