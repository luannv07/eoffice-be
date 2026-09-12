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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
    void testGetDocumentDetailsAndDownload_Success() throws Exception {
        File tempFile = File.createTempFile("test-doc", ".pdf");
        tempFile.deleteOnExit();
        Files.write(tempFile.toPath(), "Sample PDF content".getBytes());

        Document doc = new Document();
        doc.setId(1L);
        doc.setTitle("Test Doc");
        doc.setStatus("PUBLISHED");
        doc.setOfficialFilePath(tempFile.getAbsolutePath());

        lenient().when(documentRepository.findAccessiblePublishedDocument(anyLong(), anyLong()))
                .thenReturn(Optional.of(doc));
        lenient().when(documentRepository.findAccessiblePublishedDocument(any(), any(), any()))
                .thenReturn(Optional.of(doc));
        lenient().when(documentRepository.findById(anyLong()))
                .thenReturn(Optional.of(doc));

        var details = documentQueryService.getDocumentDetails(1L, 10L);
        assertNotNull(details);

        byte[] bytes = documentQueryService.downloadDocument(1L, 10L);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    void testGetDocumentDetailsAndDownload_NotFound() {
        lenient().when(documentRepository.findAccessiblePublishedDocument(eq(99L), anyLong()))
                .thenReturn(Optional.empty());
        lenient().when(documentRepository.findAccessiblePublishedDocument(eq(99L), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(documentRepository.findById(eq(99L)))
                .thenReturn(Optional.empty());

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

        lenient().when(documentRepository.findPublishedDocumentsForUser(anyLong())).thenReturn(List.of(doc));
        lenient().when(documentRepository.findAccessiblePublishedDocument(eq(1L), anyLong())).thenReturn(Optional.of(doc));
        lenient().when(documentRepository.findAccessiblePublishedDocument(eq(1L), any(), any())).thenReturn(Optional.of(doc));
        lenient().when(documentRepository.findById(eq(1L))).thenReturn(Optional.of(doc));
        lenient().when(distributionRepository.findByDocumentIdAndTargetId(eq(1L), anyLong())).thenReturn(Optional.of(distUnread));

        assertEquals(1, documentQueryService.getPublishedDocuments(10L).size());
        assertNotNull(documentQueryService.getDocumentDetail(1L, 10L));
        verify(distributionRepository, atLeastOnce()).save(any());

        when(distributionRepository.findByDocumentIdAndTargetId(eq(1L), anyLong())).thenReturn(Optional.of(distRead));
        assertNotNull(documentQueryService.getDocumentDetail(1L, 10L));

        lenient().when(documentRepository.findAccessiblePublishedDocument(eq(2L), anyLong())).thenReturn(Optional.empty());
        lenient().when(documentRepository.findAccessiblePublishedDocument(eq(2L), any(), any())).thenReturn(Optional.empty());
        lenient().when(documentRepository.findById(eq(2L))).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> documentQueryService.getDocumentDetail(2L, 10L));
    }

    @Test
    void testDownloadOfficialFile() throws Exception {
        Document doc = new Document();
        doc.setId(1L);

        lenient().when(documentRepository.findAccessiblePublishedDocument(eq(1L), anyLong())).thenReturn(Optional.of(doc));
        lenient().when(documentRepository.findAccessiblePublishedDocument(eq(1L), any(), any())).thenReturn(Optional.of(doc));
        lenient().when(documentRepository.findById(eq(1L))).thenReturn(Optional.of(doc));

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

        lenient().when(documentRepository.findAccessiblePublishedDocument(eq(2L), anyLong())).thenReturn(Optional.empty());
        lenient().when(documentRepository.findAccessiblePublishedDocument(eq(2L), any(), any())).thenReturn(Optional.empty());
        lenient().when(documentRepository.findById(eq(2L))).thenReturn(Optional.empty());
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

        lenient().when(documentRepository.findByStatusIn(any())).thenReturn(List.of(doc1, doc2, doc3));
        lenient().when(documentRepository.findAll()).thenReturn(List.of(doc1, doc2, doc3));

        var pending = documentWorkflowService.getPendingDocuments();
        assertNotNull(pending);

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

        when(mockQueryService.getPublishedDocuments(anyLong())).thenReturn(List.of());
        var resPublished = controller.getPublishedDocuments(null);
        assertNotNull(resPublished);
        assertEquals(200, resPublished.getStatusCode().value());

        when(mockQueryService.getDocumentDetails(eq(1L), anyLong())).thenReturn(java.util.Map.of("id", 1L));
        var resDetails = controller.getDocumentDetails(1L, null);
        assertNotNull(resDetails);

        when(mockQueryService.downloadDocument(eq(1L), anyLong())).thenReturn("pdf content".getBytes());
        var resDownload = controller.downloadDocument(1L, null);
        assertNotNull(resDownload);
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
    @Test
    void testWorkflowPagination_SubListBranches() {
        // Test phân trang: offset vượt quá kích thước danh sách và offset bình thường
        DocumentWorkflowService mockWf = mock(DocumentWorkflowService.class);
        DocumentWorkflowController wfController = new DocumentWorkflowController(mockWf);

        when(mockWf.getPendingDocuments()).thenReturn(List.of(
                vn.edu.epu.eoffice_be.dto.PendingDocumentResponse.builder().id(1L).title("Doc 1").build(),
                vn.edu.epu.eoffice_be.dto.PendingDocumentResponse.builder().id(2L).title("Doc 2").build()
        ));

        // Nhánh start > pendingList.size()
        var emptyPage = wfController.getPendingReviewDocuments(PageRequest.of(5, 2));
        assertNotNull(emptyPage);
        assertEquals(0, emptyPage.getBody().getContent().size());

        // Nhánh start <= pendingList.size()
        var normalPage = wfController.getPendingReviewDocuments(PageRequest.of(0, 1));
        assertNotNull(normalPage);
        assertEquals(1, normalPage.getBody().getContent().size());
    }

    @Test
    void testDocumentPublishService_ExceptionAndEdgeBranches() throws Exception {
        // Test nhánh tính SHA-256 từ file thật hợp lệ
        File realTempFile = File.createTempFile("coverage_real_doc", ".pdf");
        realTempFile.deleteOnExit();
        Files.write(realTempFile.toPath(), "Content for hashing coverage".getBytes());

        Document docWithRealFile = new Document();
        docWithRealFile.setId(77L);
        docWithRealFile.setOfficialFilePath(realTempFile.getAbsolutePath());

        when(documentRepository.findById(77L)).thenReturn(Optional.of(docWithRealFile));
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));

        PublishRequest req = new PublishRequest();
        req.setIssueDate(LocalDate.now());

        Document result = documentPublishService.publishDocument(77L, req);
        assertNotNull(result);
        assertNotNull(result.getOfficialHash());

        // Test nhánh verifyIntegrity khi file không tồn tại gây exception lúc tính hash
        docWithRealFile.setOfficialFilePath("non_existent_folder/non_existent_file.pdf");
        docWithRealFile.setOfficialHash("somehash123");
        assertFalse(documentPublishService.verifyIntegrity(77L));
    }

    @Test
    void testDocumentQueryService_RemainingBranches() {
        // Phủ các nhánh null-check và empty list của DocumentQueryService
        lenient().when(documentRepository.findPublishedDocumentsForUser(999L)).thenReturn(List.of());
        var emptyDocs = documentQueryService.getPublishedDocuments(999L);
        assertNotNull(emptyDocs);
        assertTrue(emptyDocs.isEmpty());
    }

    @Test
    void testPendingDocumentResponseDto() {
        var res = new vn.edu.epu.eoffice_be.dto.PendingDocumentResponse();
        res.setId(100L);
        res.setTitle("Tờ trình");
        res.setStatus("IN_REVIEW");

        assertEquals(100L, res.getId());
        assertEquals("Tờ trình", res.getTitle());
        assertEquals("IN_REVIEW", res.getStatus());
    }

    @Test
    void testWorkflowTimelineResponseDto() {
        var timelineRes = new vn.edu.epu.eoffice_be.dto.WorkflowTimelineResponse();
        timelineRes.setId(200L);
        timelineRes.setAction("APPROVE");
        timelineRes.setCreatedAt(LocalDateTime.now());

        assertEquals(200L, timelineRes.getId());
        assertEquals("APPROVE", timelineRes.getAction());
        assertNotNull(timelineRes.getCreatedAt());
    }
}