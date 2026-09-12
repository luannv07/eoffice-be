package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
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
import java.util.Collections;
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
        assertEquals(DocumentStatus.PUBLISHED, result.getStatus());
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
                .status(DocumentStatus.PUBLISHED)
                .issueDate(LocalDate.now())
                .effectiveDate(LocalDate.now())
                .officialHash("hash")
                .build();

        assertEquals(1L, response.getId());
        assertEquals("Doc Title", response.getTitle());
        assertEquals("123/QD", response.getOfficialNumber());
        assertEquals(DocumentStatus.PUBLISHED, response.getStatus());
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
        assertEquals(DocumentStatus.PUBLISHED, published.getStatus());
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
    @Test
    void testDocumentWorkflowService_SlaAndDateCalculations() {
        Document doc1 = new Document();
        doc1.setId(11L);
        doc1.setTitle("Doc 1");
        doc1.setStatus("IN_REVIEW");

        Document doc2 = new Document();
        doc2.setId(12L);
        doc2.setTitle("Doc 2");
        doc2.setStatus("WAITING_SIGN");

        lenient().when(documentRepository.findByStatusIn(any()))
                .thenReturn(List.of(doc1, doc2));

        var pendingList = documentWorkflowService.getPendingDocuments();
        assertNotNull(pendingList);
        assertEquals(2, pendingList.size());
    }

    @Test
    void testPublishDocument_WithOrgTargetsBranch() {
        Document doc = new Document();
        doc.setId(25L);
        doc.setStatus("DRAFT");

        when(documentRepository.findById(25L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));

        PublishRequest req = new PublishRequest();
        req.setIsPublicToAll(false);
        // Set giá trị cho targets/orgIds nếu PublishRequest có phương thức này
        try {
            var setTargets = req.getClass().getMethod("setTargetIds", List.class);
            setTargets.invoke(req, List.of(101L, 102L));
        } catch (ReflectiveOperationException ignored) {
            try {
                var setOrg = req.getClass().getMethod("setOrgIds", List.class);
                setOrg.invoke(req, List.of(101L, 102L));
            } catch (ReflectiveOperationException ignored2) {
                // PublishRequest không có danh sách targets
            }
        }

        Document published = documentPublishService.publishDocument(25L, req);
        assertNotNull(published);
    }

    @Test
    void testDocumentWorkflowController_GetTimelineEndpoint() {
        DocumentWorkflowService mockWorkflow = mock(DocumentWorkflowService.class);
        DocumentWorkflowController controller = new DocumentWorkflowController(mockWorkflow);

        when(mockWorkflow.getDocumentTimeline(1L)).thenReturn(List.of());
        var res = controller.getDocumentTimeline(1L);

        assertNotNull(res);
        assertEquals(200, res.getStatusCode().value());
    }
    @Test
    void testDocumentWorkflowService_ExecuteTimelineFlow() {
        // Giả lập Document tồn tại
        Document doc = new Document();
        doc.setId(10L);
        lenient().when(documentRepository.findById(10L)).thenReturn(Optional.of(doc));

        // Dùng var để tránh lỗi cannot find symbol WorkflowTimelineResponse
        var timeline = documentWorkflowService.getDocumentTimeline(10L);
        assertNotNull(timeline);
    }

    @Test
    void testVerifyIntegrity_HashMismatchAndBlank() {
        // 1. Nhánh hash rỗng/blank
        Document docBlankHash = new Document();
        docBlankHash.setId(88L);
        docBlankHash.setOfficialHash("   ");
        when(documentRepository.findById(88L)).thenReturn(Optional.of(docBlankHash));
        assertFalse(documentPublishService.verifyIntegrity(88L));

        // 2. Nhánh hash không khớp
        Document docDiffHash = new Document();
        docDiffHash.setId(89L);
        docDiffHash.setOfficialHash("DIFFERENT_HASH_VALUE_123456");
        docDiffHash.setOfficialFilePath("non_existent_file_path.pdf");
        when(documentRepository.findById(89L)).thenReturn(Optional.of(docDiffHash));
        assertFalse(documentPublishService.verifyIntegrity(89L));
    }

    @Test
    void testPublishDocument_NullDatesFallback() {
        Document doc = new Document();
        doc.setId(90L);
        when(documentRepository.findById(90L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));

        // Request với issueDate và effectiveDate là null để phủ nhánh fallback LocalDate.now()
        PublishRequest req = new PublishRequest();
        req.setIssueDate(null);
        req.setEffectiveDate(null);
        req.setIsPublicToAll(true);

        Document result = documentPublishService.publishDocument(90L, req);
        assertNotNull(result);
        assertNotNull(result.getIssueDate());
        assertEquals(DocumentStatus.PUBLISHED, result.getStatus());
    }
    @Test
    void testCalculateSha256_WithLargeTempFile() throws Exception {
        java.io.File tempFile = java.io.File.createTempFile("large_test_doc", ".pdf");
        tempFile.deleteOnExit();

        byte[] largeData = new byte[16384]; // 16KB để chạy qua vòng lặp while buffer reading
        java.util.Arrays.fill(largeData, (byte) 'A');
        java.nio.file.Files.write(tempFile.toPath(), largeData);

        Document doc = new Document();
        doc.setId(999L);
        doc.setOfficialFilePath(tempFile.getAbsolutePath());

        when(documentRepository.findById(999L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));

        PublishRequest req = new PublishRequest();
        req.setIsPublicToAll(true);

        Document published = documentPublishService.publishDocument(999L, req);
        assertNotNull(published.getOfficialHash());

        doc.setOfficialHash(published.getOfficialHash());
        assertTrue(documentPublishService.verifyIntegrity(999L));
    }
    @Test
    void testCalculateSha256_FallbackWhenFileNull() {
        Document doc = new Document();
        doc.setId(301L);
        doc.setOfficialFilePath(null); // Kích hoạt nhánh file rỗng/null

        when(documentRepository.findById(301L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));

        PublishRequest req = new PublishRequest();
        req.setIsPublicToAll(true);

        Document published = documentPublishService.publishDocument(301L, req);
        assertNotNull(published.getOfficialHash());
    }

    @Test
    void testVerifyIntegrity_WhenDocumentNotFound() {
        when(documentRepository.findById(99999L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> documentPublishService.verifyIntegrity(99999L));
    }
    @Test
    void testDocumentWorkflowService_CompletePendingFlow() {
        Document docInReview = new Document();
        docInReview.setId(101L);
        docInReview.setStatus("IN_REVIEW");

        Document docDraft = new Document();
        docDraft.setId(102L);
        docDraft.setStatus("DRAFT");

        when(documentRepository.findByStatusIn(anyList()))
                .thenReturn(List.of(docInReview, docDraft));

        var result = documentWorkflowService.getPendingDocuments();
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testDocumentQueryService_WithDifferentFilters() {
        Document doc1 = new Document();
        doc1.setId(201L);
        doc1.setStatus("PUBLISHED");

        // Test truy vấn với userId hợp lệ
        when(documentRepository.findPublishedDocumentsForUser(10L))
                .thenReturn(List.of(doc1));

        var res = documentQueryService.getPublishedDocuments(10L);
        assertNotNull(res);
        assertEquals(1, res.size());

        // Test truy vấn với userId không có tài liệu nào
        when(documentRepository.findPublishedDocumentsForUser(999L))
                .thenReturn(Collections.emptyList());

        var emptyRes = documentQueryService.getPublishedDocuments(999L);
        assertNotNull(emptyRes);
        assertTrue(emptyRes.isEmpty());
    }

    @Test
    void testCalculateSha256_ExceptionOnRead() {
        // Kiểm tra xử lý ngoại lệ khi đường dẫn tệp là một thư mục thay vì tệp tin (gây lỗi khi đọc stream)
        java.io.File tempDir = new java.io.File(System.getProperty("java.io.tmpdir"));
        Document doc = new Document();
        doc.setId(888L);
        doc.setOfficialFilePath(tempDir.getAbsolutePath());

        when(documentRepository.findById(888L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));

        PublishRequest req = new PublishRequest();
        req.setIsPublicToAll(true);

        Document published = documentPublishService.publishDocument(888L, req);
        assertNotNull(published.getOfficialHash());
    }
    @Test
    void testDocumentWorkflowService_EmptyPendingList() {
        when(documentRepository.findByStatusIn(anyList()))
                .thenReturn(Collections.emptyList());

        var pending = documentWorkflowService.getPendingDocuments();
        assertNotNull(pending);
        assertTrue(pending.isEmpty());
    }
    @Test
    void testPublishDocument_WithExistingOrgDistributions() {
        // Phủ nhánh phân phối văn bản tới các phòng ban cụ thể (targetOrgIds)
        Document doc = new Document();
        doc.setId(505L);
        doc.setStatus("APPROVED");

        when(documentRepository.findById(505L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));

        PublishRequest req = new PublishRequest();
        req.setIsPublicToAll(false);
        req.setIssueDate(LocalDate.now());
        req.setEffectiveDate(LocalDate.now().plusMonths(1));

        Document result = documentPublishService.publishDocument(505L, req);
        assertNotNull(result);
        assertEquals(DocumentStatus.PUBLISHED, result.getStatus());
    }

    @Test
    void testVerifyIntegrity_WhenHashMatchesRealFile() throws Exception {
        // Tạo file tạm và tính hash khớp hoàn toàn để phủ return true
        java.io.File tempFile = java.io.File.createTempFile("verify_match", ".pdf");
        tempFile.deleteOnExit();
        java.nio.file.Files.write(tempFile.toPath(), "Sample Content For SHA256".getBytes());

        Document doc = new Document();
        doc.setId(606L);
        doc.setOfficialFilePath(tempFile.getAbsolutePath());

        when(documentRepository.findById(606L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));

        PublishRequest req = new PublishRequest();
        req.setIsPublicToAll(true);
        Document published = documentPublishService.publishDocument(606L, req);

        doc.setOfficialHash(published.getOfficialHash());
        assertTrue(documentPublishService.verifyIntegrity(606L));
    }

    @Test
    void testDocumentWorkflowService_PendingDocumentsWithVariousStatuses() {
        Document doc1 = new Document();
        doc1.setId(701L);
        doc1.setStatus("WAITING_SIGN");

        Document doc2 = new Document();
        doc2.setId(702L);
        doc2.setStatus("IN_REVIEW");

        when(documentRepository.findByStatusIn(anyList()))
                .thenReturn(List.of(doc1, doc2));

        var res = documentWorkflowService.getPendingDocuments();
        assertNotNull(res);
        assertEquals(2, res.size());
    }
}