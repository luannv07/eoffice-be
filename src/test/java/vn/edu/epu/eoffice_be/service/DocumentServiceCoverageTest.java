package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import vn.edu.epu.eoffice_be.controller.PublicController;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentDistribution;
import vn.edu.epu.eoffice_be.repository.DocumentDistributionRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceCoverageTest {

    @Mock
    DocumentRepository documentRepository;

    @Mock
    DocumentDistributionRepository distributionRepository;

    @InjectMocks
    DocumentQueryService documentQueryService;

    @InjectMocks
    DocumentPublishService documentPublishService;

    @Test
    void testGetDocumentDetailsAndDownload_Success() {
        Document doc = new Document();
        doc.setId(1L);
        doc.setTitle("Test Doc");
        doc.setStatus("PUBLISHED");

        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));

        assertNotNull(documentQueryService.getDocumentDetails(1L, 10L));
        byte[] bytes = documentQueryService.downloadDocument(1L, 10L);
        assertTrue(bytes.length > 0);

        byte[] bytesNullOrg = documentQueryService.downloadDocument(1L, null);
        assertTrue(bytesNullOrg.length > 0);
    }

    @Test
    void testGetDocumentDetailsAndDownload_NotFound() {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> documentQueryService.getDocumentDetails(99L, 10L));
        assertThrows(IllegalArgumentException.class, () -> documentQueryService.downloadDocument(99L, 10L));
    }

    @Test
    void testGetPublishedDocumentsAndDetail() {
        Document doc = new Document();
        doc.setId(1L);
        doc.setTitle("Test Doc");
        doc.setOfficialNumber("01/QD");
        doc.setStatus("PUBLISHED");
        doc.setIssueDate(LocalDate.now());

        DocumentDistribution distUnread = new DocumentDistribution();
        distUnread.setIsRead(false);

        DocumentDistribution distRead = new DocumentDistribution();
        distRead.setIsRead(true);

        when(documentRepository.findPublishedDocumentsForUser(10L)).thenReturn(List.of(doc));
        when(documentRepository.findAccessiblePublishedDocument(1L, 10L)).thenReturn(Optional.of(doc));
        when(distributionRepository.findByDocumentIdAndTargetId(1L, 10L)).thenReturn(Optional.of(distUnread));

        assertEquals(1, documentQueryService.getPublishedDocuments(10L).size());
        assertNotNull(documentQueryService.getDocumentDetail(1L, 10L));
        verify(distributionRepository, times(1)).save(any());

        // Test nhánh đã đọc
        when(distributionRepository.findByDocumentIdAndTargetId(1L, 10L)).thenReturn(Optional.of(distRead));
        assertNotNull(documentQueryService.getDocumentDetail(1L, 10L));

        // Test nhánh không tìm thấy document
        when(documentRepository.findAccessiblePublishedDocument(2L, 10L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> documentQueryService.getDocumentDetail(2L, 10L));
    }

    @Test
    void testDownloadOfficialFile() throws Exception {
        Document doc = new Document();
        doc.setId(1L);

        // Nhánh file path null / blank
        when(documentRepository.findAccessiblePublishedDocument(1L, 10L)).thenReturn(Optional.of(doc));
        assertThrows(IllegalArgumentException.class, () -> 
            documentQueryService.downloadOfficialFile(1L, 10L, "user", "127.0.0.1"));

        doc.setOfficialFilePath("   ");
        assertThrows(IllegalArgumentException.class, () -> 
            documentQueryService.downloadOfficialFile(1L, 10L, "user", "127.0.0.1"));

        // Nhánh file không tồn tại trên đĩa
        doc.setOfficialFilePath("non_existing_file.pdf");
        assertThrows(IllegalArgumentException.class, () -> 
            documentQueryService.downloadOfficialFile(1L, 10L, "user", "127.0.0.1"));

        // Nhánh thành công với file tạm
        File tempFile = File.createTempFile("test_doc", ".pdf");
        tempFile.deleteOnExit();
        doc.setOfficialFilePath(tempFile.getAbsolutePath());

        Resource res = documentQueryService.downloadOfficialFile(1L, 10L, "user", "127.0.0.1");
        assertNotNull(res);
        assertTrue(res.exists());

        // Nhánh không có quyền truy cập
        when(documentRepository.findAccessiblePublishedDocument(2L, 10L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> 
            documentQueryService.downloadOfficialFile(2L, 10L, "user", "127.0.0.1"));
    }

    @Test
    void testVerifyIntegrity() {
        Document doc = new Document();
        doc.setId(1L);
        doc.setOfficialHash("abc123hash");

        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        assertTrue(documentPublishService.verifyIntegrity(1L));

        doc.setOfficialHash(null);
        assertFalse(documentPublishService.verifyIntegrity(1L));

        doc.setOfficialHash("   ");
        assertFalse(documentPublishService.verifyIntegrity(1L));

        when(documentRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> documentPublishService.verifyIntegrity(99L));
    }

    @Test
    void testPublicControllerHealth() {
        PublicController controller = new PublicController();
        assertNotNull(controller.health());
    }
}