package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import vn.edu.epu.eoffice_be.controller.DocumentWorkflowController;
import vn.edu.epu.eoffice_be.controller.PublicController;
import vn.edu.epu.eoffice_be.dto.PublishRequest;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentDistribution;
import vn.edu.epu.eoffice_be.entity.DocumentWorkflowLog;
import vn.edu.epu.eoffice_be.repository.DocumentDistributionRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentWorkflowLogRepository;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
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

    @Mock
    DocumentWorkflowLogRepository workflowLogRepository;

    @InjectMocks
    DocumentQueryService documentQueryService;

    @InjectMocks
    DocumentPublishService documentPublishService;

    @InjectMocks
    DocumentWorkflowService documentWorkflowService;

    @Test
    void testGetDocumentDetailsAndDownload_Success() {
        Document doc = new Document();
        doc.setId(1L);
        doc.setTitle("Test Doc");
        doc.setStatus("PUBLISHED");

        // Mock method kiểm tra quyền thay cho findById
        when(documentRepository.findAccessiblePublishedDocument(1L, 10L)).thenReturn(Optional.of(doc));
        when(documentRepository.findAccessiblePublishedDocument(1L, null)).thenReturn(Optional.of(doc));

        assertNotNull(documentQueryService.getDocumentDetails(1L, 10L));
        byte[] bytes = documentQueryService.downloadDocument(1L, 10L);
        assertTrue(bytes.length > 0);

        byte[] bytesNullOrg = documentQueryService.downloadDocument(1L, null);
        assertTrue(bytesNullOrg.length > 0);
    }

    @Test
    void testGetDocumentDetailsAndDownload_NotFound() {
        when(documentRepository.findAccessiblePublishedDocument(99L, 10L)).thenReturn(Optional.empty());

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

        when(distributionRepository.findByDocumentIdAndTargetId(1L, 10L)).thenReturn(Optional.of(distRead));
        assertNotNull(documentQueryService.getDocumentDetail(1L, 10L));

        when(documentRepository.findAccessiblePublishedDocument(2L, 10L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> documentQueryService.getDocumentDetail(2L, 10L));
    }

    @Test
    void testDownloadOfficialFile() throws Exception {
        Document doc = new Document();
        doc.setId(1L);

        when(documentRepository.findAccessiblePublishedDocument(1L, 10L)).thenReturn(Optional.of(doc));
        assertThrows(IllegalArgumentException.class, () ->
                documentQueryService.downloadOfficialFile(1L, 10L, "user", "127.0.0.1"));

        doc.setOfficialFilePath("   ");
        assertThrows(IllegalArgumentException.class, () ->
                documentQueryService.downloadOfficialFile(1L, 10L, "user", "127.0.0.1"));

        doc.setOfficialFilePath("non_existing_file.pdf");
        assertThrows(IllegalArgumentException.class, () ->
                documentQueryService.downloadOfficialFile(1L, 10L, "user", "127.0.0.1"));

        File tempFile = File.createTempFile("test_doc", ".pdf");
        tempFile.deleteOnExit();
        doc.setOfficialFilePath(tempFile.getAbsolutePath());

        Resource res = documentQueryService.downloadOfficialFile(1L, 10L, "user", "127.0.0.1");
        assertNotNull(res);
        assertTrue(res.exists());

        when(documentRepository.findAccessiblePublishedDocument(2L, 10L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () ->
                documentQueryService.downloadOfficialFile(2L, 10L, "user", "127.0.0.1"));
    }

    @Test
    void testVerifyIntegrity() throws Exception {
        File tempFile = File.createTempFile("verify_hash", ".pdf");
        tempFile.deleteOnExit();
        byte[] content = "E-Office Integrity Test Content".getBytes();
        Files.write(tempFile.toPath(), content);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String expectedHash = HexFormat.of().formatHex(digest.digest(content));

        Document doc = new Document();
        doc.setId(1L);
        doc.setOfficialFilePath(tempFile.getAbsolutePath());
        doc.setOfficialHash(expectedHash);

        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        assertTrue(documentPublishService.verifyIntegrity(1L));

        doc.setOfficialHash(null);
        assertFalse(documentPublishService.verifyIntegrity(1L));

        doc.setOfficialHash("   ");
        assertFalse(documentPublishService.verifyIntegrity(1L));

        doc.setOfficialHash("wrong_hash_123");
        assertFalse(documentPublishService.verifyIntegrity(1L));

        when(documentRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> documentPublishService.verifyIntegrity(99L));
    }

    @Test
    void testPublishDocument() {
        Document doc = new Document();
        doc.setId(1L);

        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PublishRequest req = new PublishRequest();
        req.setIssueDate(LocalDate.now());

        Document result = documentPublishService.publishDocument(1L, req);
        assertNotNull(result);
        assertEquals("01/QĐ-ĐHL", result.getOfficialNumber());
        assertEquals("PUBLISHED", result.getStatus());
        assertNotNull(result.getOfficialHash());

        // Báo lỗi nếu ID tài liệu không tồn tại
        when(documentRepository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> documentPublishService.publishDocument(2L, req));
    }

    @Test
    void testDocumentWorkflowService_Coverage() {
        Document doc1 = new Document();
        doc1.setId(1L);
        doc1.setStatus("IN_REVIEW");

        Document doc2 = new Document();
        doc2.setId(2L);
        doc2.setStatus("WAITING_SIGN");

        Document doc3 = new Document();
        doc3.setId(3L);
        doc3.setStatus("OTHER");

        when(documentRepository.findAll()).thenReturn(List.of(doc1, doc2, doc3));

        var pending = documentWorkflowService.getPendingDocuments();
        assertNotNull(pending);

        // Khởi tạo thực thể bất biến bằng Builder
        DocumentWorkflowLog log = DocumentWorkflowLog.builder()
                .id(10L)
                .documentId(1L)
                .action("SUBMIT")
                .createdAt(LocalDateTime.now())
                .build();

        when(workflowLogRepository.findByDocumentIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(log));

        var timeline = documentWorkflowService.getDocumentTimeline(1L);
        assertNotNull(timeline);

        documentWorkflowService.logAction(1L, "SIGN", "GiamDoc", "Ky duyet", "127.0.0.1");
        verify(workflowLogRepository, atLeastOnce()).save(any());
    }

    @Test
    void testDocumentWorkflowController() {
        DocumentWorkflowService mockWorkflow = mock(DocumentWorkflowService.class);
        DocumentWorkflowController controller = new DocumentWorkflowController(mockWorkflow);

        assertNotNull(controller.getPendingReviewDocuments(PageRequest.of(0, 10)));
        assertNotNull(controller.getDocumentTimeline(1L));
    }

    @Test
    void testDocumentResponseDto() {
        DocumentResponse response = DocumentResponse.builder()
                .id(1L)
                .title("Doc Title")
                .officialNumber("123/QD")
                .status("PUBLISHED")
                .issueDate(LocalDate.now())
                .effectiveDate(LocalDate.now())
                .officialHash("hash")
                .build();

        assertEquals(1L, response.getId());
        assertEquals("Doc Title", response.getTitle());
        assertEquals("123/QD", response.getOfficialNumber());
        assertEquals("PUBLISHED", response.getStatus());
        assertNotNull(response.getIssueDate());
        assertNotNull(response.getEffectiveDate());
        assertEquals("hash", response.getOfficialHash());
    }

    @Test
    void testPublicControllerHealth() {
        PublicController controller = new PublicController();
        assertNotNull(controller.health());
    }
    @Test
    void testDocumentQueryController_Endpoints() {
        DocumentQueryService mockQueryService = mock(DocumentQueryService.class);
        vn.edu.epu.eoffice_be.controller.DocumentQueryController controller =
                new vn.edu.epu.eoffice_be.controller.DocumentQueryController(mockQueryService);

        // Test getPublishedDocuments(Authentication)
        when(mockQueryService.getPublishedDocuments(anyLong())).thenReturn(List.of());
        var resPublished = controller.getPublishedDocuments(null);
        assertNotNull(resPublished);
        assertEquals(200, resPublished.getStatusCode().value());

        // Test getDocumentDetails(Long id, Long orgId)
        when(mockQueryService.getDocumentDetails(1L, 10L)).thenReturn(java.util.Map.of("id", 1L));
        var resDetails = controller.getDocumentDetails(1L, 10L);
        assertNotNull(resDetails);
        assertEquals(200, resDetails.getStatusCode().value());

        // Test downloadDocument(Long id, Authentication authentication)
        when(mockQueryService.downloadDocument(1L, 1L)).thenReturn("dummy pdf content".getBytes());
        var resDownload = controller.downloadDocument(1L, null);
        assertNotNull(resDownload);
        assertEquals(200, resDownload.getStatusCode().value());
        assertNotNull(resDownload.getBody());
    }

    @Test
    void testDocumentPublishService_NullEdgeCases() {
        Document doc = new Document();
        doc.setId(10L);
        doc.setStatus("DRAFT");

        when(documentRepository.findById(10L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Document published = documentPublishService.publishDocument(10L, null);
        assertNotNull(published);
        assertEquals("10/QĐ-ĐHL", published.getOfficialNumber());
        assertEquals("PUBLISHED", published.getStatus());
    }
}