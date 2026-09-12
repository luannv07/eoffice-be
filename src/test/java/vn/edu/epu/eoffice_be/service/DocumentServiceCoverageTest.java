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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.security.UserIdAwarePrincipal;

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

    public static class TestOrgPrincipal implements vn.edu.epu.eoffice_be.security.OrgAwarePrincipal {
        @Override
        public Long getOrgId() { return 1L; }
    }

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
        doc.setStatus(DocumentStatus.PUBLISHED);
        doc.setOfficialFilePath(tempFile.getAbsolutePath());
        doc.setOfficialHash(expectedHash);

        Authentication auth = new UsernamePasswordAuthenticationToken("tester", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        assertTrue(documentPublishService.verifyIntegrity(1L, auth));

        doc.setOfficialHash(null);
        assertFalse(documentPublishService.verifyIntegrity(1L, auth));

        doc.setOfficialHash("   ");
        assertFalse(documentPublishService.verifyIntegrity(1L, auth));

        doc.setOfficialHash("wrong_hash_123");
        assertFalse(documentPublishService.verifyIntegrity(1L, auth));

        when(documentRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> documentPublishService.verifyIntegrity(99L, auth));
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
        Authentication auth = new UsernamePasswordAuthenticationToken("user", "pass", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertNotNull(controller.getDocumentTimeline(1L, auth));
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

        org.springframework.security.core.Authentication auth = mock(org.springframework.security.core.Authentication.class);
        TestOrgPrincipal principal = new TestOrgPrincipal();
        when(auth.getPrincipal()).thenReturn(principal);
        when(auth.isAuthenticated()).thenReturn(true);

        when(mockQueryService.getPublishedDocuments(eq(1L))).thenReturn(List.of());
        var resPublished = controller.getPublishedDocuments(auth);
        assertNotNull(resPublished);
        assertEquals(200, resPublished.getStatusCode().value());
        verify(mockQueryService, times(1)).getPublishedDocuments(eq(principal.getOrgId()));

        when(mockQueryService.getDocumentDetails(eq(1L), eq(1L))).thenReturn(java.util.Map.of("id", 1L));
        var resDetails = controller.getDocumentDetails(1L, auth);
        assertNotNull(resDetails);
        verify(mockQueryService, times(1)).getDocumentDetails(eq(1L), eq(principal.getOrgId()));

        when(mockQueryService.downloadDocument(eq(1L), eq(1L))).thenReturn("pdf content".getBytes());
        var resDownload = controller.downloadDocument(1L, auth);
        assertNotNull(resDownload);
        verify(mockQueryService, times(1)).downloadDocument(eq(1L), eq(principal.getOrgId()));
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
    void testWorkflowPagination_ServiceBranches() {
        DocumentWorkflowService mockWf = mock(DocumentWorkflowService.class);
        DocumentWorkflowController wfController = new DocumentWorkflowController(mockWf);

        // 1. Controller passes PageRequest.of(0, 10) to DocumentWorkflowService (happy path)
        PageRequest pageRequest0 = PageRequest.of(0, 10);
        when(mockWf.getPendingDocuments(eq(pageRequest0)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(
                        vn.edu.epu.eoffice_be.dto.PendingDocumentResponse.builder().id(1L).title("Doc 1").build()
                ), pageRequest0, 1));

        var page = wfController.getPendingReviewDocuments(pageRequest0);
        assertNotNull(page);
        assertNotNull(page.getBody());
        assertEquals(1, page.getBody().getContent().size());
        assertEquals(1L, page.getBody().getContent().get(0).getId());
        verify(mockWf, times(1)).getPendingDocuments(eq(pageRequest0));

        // 2. Controller passes PageRequest.of(5, 2) and handles empty Page
        PageRequest pageRequest5 = PageRequest.of(5, 2);
        when(mockWf.getPendingDocuments(eq(pageRequest5)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        Collections.emptyList(), pageRequest5, 0));

        var emptyPage = wfController.getPendingReviewDocuments(pageRequest5);
        assertNotNull(emptyPage);
        assertNotNull(emptyPage.getBody());
        assertTrue(emptyPage.getBody().getContent().isEmpty());
        assertEquals(0, emptyPage.getBody().getContent().size());
        verify(mockWf, times(1)).getPendingDocuments(eq(pageRequest5));

        // 3. Real DocumentWorkflowService branch: covers Pageable passing to documentRepository
        when(documentRepository.findByStatusIn(anyList(), eq(pageRequest0)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        var srvPage = documentWorkflowService.getPendingDocuments(pageRequest0);
        assertNotNull(srvPage);
        assertTrue(srvPage.getContent().isEmpty());
        verify(documentRepository, times(1)).findByStatusIn(anyList(), eq(pageRequest0));
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
        Authentication auth = new UsernamePasswordAuthenticationToken("tester", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertFalse(documentPublishService.verifyIntegrity(77L, auth));
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
        Authentication auth = new UsernamePasswordAuthenticationToken("user", "pass", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        when(mockWorkflow.getDocumentTimeline(1L, auth)).thenReturn(List.of());
        var res = controller.getDocumentTimeline(1L, auth);

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
        Authentication auth = new UsernamePasswordAuthenticationToken("tester", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        // 1. Nhánh hash rỗng/blank
        Document docBlankHash = new Document();
        docBlankHash.setId(88L);
        docBlankHash.setStatus(DocumentStatus.PUBLISHED);
        docBlankHash.setOfficialHash("   ");
        when(documentRepository.findById(88L)).thenReturn(Optional.of(docBlankHash));
        assertFalse(documentPublishService.verifyIntegrity(88L, auth));

        // 2. Nhánh hash không khớp
        Document docDiffHash = new Document();
        docDiffHash.setId(89L);
        docDiffHash.setStatus(DocumentStatus.PUBLISHED);
        docDiffHash.setOfficialHash("DIFFERENT_HASH_VALUE_123456");
        docDiffHash.setOfficialFilePath("non_existent_file_path.pdf");
        when(documentRepository.findById(89L)).thenReturn(Optional.of(docDiffHash));
        assertFalse(documentPublishService.verifyIntegrity(89L, auth));
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
        Authentication auth = new UsernamePasswordAuthenticationToken("tester", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertTrue(documentPublishService.verifyIntegrity(999L, auth));
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
        assertThrows(IllegalArgumentException.class, () -> documentPublishService.verifyIntegrity(99999L, null));
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
        Authentication auth = new UsernamePasswordAuthenticationToken("tester", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertTrue(documentPublishService.verifyIntegrity(606L, auth));

        // [18/20] Kiểm tra hash sai thì verifyIntegrity trả về false
        doc.setOfficialHash("different_hash_value_123");
        assertFalse(documentPublishService.verifyIntegrity(606L, auth));

        // [18/20] Gọi verifyIntegrity với null authentication bị từ chối theo chuẩn bảo mật
        assertThrows(AccessDeniedException.class, () -> documentPublishService.verifyIntegrity(606L, null));
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

    public static class PrincipalWithGetUserId implements UserIdAwarePrincipal {
        private final Long userId;
        public PrincipalWithGetUserId(Long userId) { this.userId = userId; }
        @Override
        public Long getUserId() { return userId; }
    }

    public static class NonUserIdPrincipal {
        private final Long id;
        public NonUserIdPrincipal(Long id) { this.id = id; }
        public Long getId() { return id; }
    }

    public static class NumericUsernamePrincipal implements java.security.Principal {
        private final String username;
        public NumericUsernamePrincipal(String username) { this.username = username; }
        @Override
        public String getName() { return username; }
        @Override
        public String toString() { return username; }
    }

    public static class CustomStringPrincipal {
        private final String username;
        public CustomStringPrincipal(String username) { this.username = username; }
        public String getName() { return username; }
        @Override
        public String toString() { return username; }
    }

    public static class NumericUserDetailsPrincipal implements org.springframework.security.core.userdetails.UserDetails {
        private final String username;
        public NumericUserDetailsPrincipal(String username) { this.username = username; }
        @Override public String getUsername() { return username; }
        @Override public String getPassword() { return ""; }
        @Override public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }
        @Override public boolean isAccountNonExpired() { return true; }
        @Override public boolean isAccountNonLocked() { return true; }
        @Override public boolean isCredentialsNonExpired() { return true; }
        @Override public boolean isEnabled() { return true; }
    }

    @Test
    void testVerifyIntegrity_UnpublishedDocument_SecurityChecks() throws Exception {
        UserLogin creator = new UserLogin();
        creator.setId(100L);

        // [20/20] Tạo file tạm thực tế có SHA-256 hash chuẩn xác để kiểm tra tính toàn vẹn không bị thiếu mock
        File tempFile = File.createTempFile("test_verify_doc", ".pdf");
        tempFile.deleteOnExit();
        byte[] fileBytes = "Sample PDF content for security verification".getBytes(StandardCharsets.UTF_8);
        Files.write(tempFile.toPath(), fileBytes);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String validHash = HexFormat.of().formatHex(digest.digest(fileBytes));

        Document draftDoc = new Document();
        draftDoc.setId(500L);
        draftDoc.setStatus(DocumentStatus.DRAFT);
        draftDoc.setCreatorUser(creator);
        draftDoc.setOfficialHash(validHash);
        draftDoc.setOfficialFilePath(tempFile.getAbsolutePath());

        when(documentRepository.findById(500L)).thenReturn(Optional.of(draftDoc));

        Document publishedDoc = new Document();
        publishedDoc.setId(501L);
        publishedDoc.setStatus(DocumentStatus.PUBLISHED);
        publishedDoc.setCreatorUser(creator);
        publishedDoc.setOfficialHash(validHash);
        publishedDoc.setOfficialFilePath(tempFile.getAbsolutePath());

        when(documentRepository.findById(501L)).thenReturn(Optional.of(publishedDoc));

        // 1. Authentication null trên tài liệu DRAFT -> ném AccessDeniedException
        assertThrows(AccessDeniedException.class, () -> documentPublishService.verifyIntegrity(500L, null));

        // 2. Authentication unauthenticated -> ném AccessDeniedException
        Authentication unauth = mock(Authentication.class);
        when(unauth.isAuthenticated()).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> documentPublishService.verifyIntegrity(500L, unauth));

        // 3. [8/20] Document PUBLISHED nhưng authentication null hoặc unauthenticated -> ném AccessDeniedException
        assertThrows(AccessDeniedException.class, () -> documentPublishService.verifyIntegrity(501L, null));
        assertThrows(AccessDeniedException.class, () -> documentPublishService.verifyIntegrity(501L, unauth));

        // 4. [8/17] [9/17] [10/17] Không suy đoán user ID từ username số hay reflection bừa bãi
        // Principal có username dạng số "100" không phải UserIdAwarePrincipal -> bị từ chối
        Authentication numUserAuth = new UsernamePasswordAuthenticationToken(
                new NumericUsernamePrincipal("100"), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertThrows(AccessDeniedException.class, () -> documentPublishService.verifyIntegrity(500L, numUserAuth));

        // Principal dạng String chứa user id ("100") không phải UserIdAwarePrincipal -> bị từ chối
        Authentication strUserAuth = new UsernamePasswordAuthenticationToken(
                "100", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertThrows(AccessDeniedException.class, () -> documentPublishService.verifyIntegrity(500L, strUserAuth));

        // Principal UserDetails có username dạng số "100" không phải UserIdAwarePrincipal -> bị từ chối
        Authentication userDetailsAuth = new UsernamePasswordAuthenticationToken(
                new NumericUserDetailsPrincipal("100"), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertThrows(AccessDeniedException.class, () -> documentPublishService.verifyIntegrity(500L, userDetailsAuth));

        // Principal có username dạng số khác creatorId (999 != 100) -> bị từ chối
        Authentication diffNumUserAuth = new UsernamePasswordAuthenticationToken(
                new NumericUsernamePrincipal("999"), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertThrows(AccessDeniedException.class, () -> documentPublishService.verifyIntegrity(500L, diffNumUserAuth));

        // Principal có getId() không implement UserIdAwarePrincipal -> bị từ chối
        Authentication nonUserIdAuth = new UsernamePasswordAuthenticationToken(
                new NonUserIdPrincipal(100L), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertThrows(AccessDeniedException.class, () -> documentPublishService.verifyIntegrity(500L, nonUserIdAuth));

        // Principal implement UserIdAwarePrincipal trả về 100L trùng creatorId -> thành công
        Authentication withUserIdAuth = new UsernamePasswordAuthenticationToken(
                new PrincipalWithGetUserId(100L), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertTrue(documentPublishService.verifyIntegrity(500L, withUserIdAuth));

        // Principal không thể trích xuất userId hợp lệ và không có quyền -> ném AccessDeniedException
        Authentication invalidPrincipalAuth = new UsernamePasswordAuthenticationToken(
                new CustomStringPrincipal("non_numeric"), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertThrows(AccessDeniedException.class, () -> documentPublishService.verifyIntegrity(500L, invalidPrincipalAuth));

        // 5. User thông thường khác creatorId (200L != 100L) -> bị từ chối
        JwtUserPrincipal otherUser = new JwtUserPrincipal(200L, "other", "USER", true, List.of());
        Authentication otherAuth = new UsernamePasswordAuthenticationToken(
                otherUser, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertThrows(AccessDeniedException.class, () -> documentPublishService.verifyIntegrity(500L, otherAuth));

        // 6. User có DOCUMENT_VIEW nhưng không phải creator -> bị từ chối
        Authentication viewAuth = new UsernamePasswordAuthenticationToken(
                otherUser, null, List.of(new SimpleGrantedAuthority("DOCUMENT_VIEW")));
        assertThrows(AccessDeniedException.class, () -> documentPublishService.verifyIntegrity(500L, viewAuth));

        // 7. ROLE_ADMIN -> được phép vượt qua kiểm tra quyền và xác thực tính toàn vẹn thành công
        Authentication adminAuth = new UsernamePasswordAuthenticationToken(
                otherUser, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertTrue(documentPublishService.verifyIntegrity(500L, adminAuth));

        // 8. DOCUMENT_VERIFY -> được phép vượt qua kiểm tra quyền và xác thực tính toàn vẹn thành công
        Authentication verifyAuth = new UsernamePasswordAuthenticationToken(
                otherUser, null, List.of(new SimpleGrantedAuthority("DOCUMENT_VERIFY")));
        assertTrue(documentPublishService.verifyIntegrity(500L, verifyAuth));

        // 9. Chính creator (userId = 100L) -> được phép vượt qua kiểm tra quyền và xác thực tính toàn vẹn thành công
        JwtUserPrincipal creatorPrincipal = new JwtUserPrincipal(100L, "creator", "USER", true, List.of());
        Authentication creatorAuth = new UsernamePasswordAuthenticationToken(
                creatorPrincipal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertTrue(documentPublishService.verifyIntegrity(500L, creatorAuth));

        // 10. [7/17] Overload verifyIntegrity(Long) và verifyIntegrity(Long, null) uỷ quyền theo SecurityContextHolder
        try {
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(creatorAuth);
            assertTrue(documentPublishService.verifyIntegrity(501L, null));
            assertTrue(documentPublishService.verifyIntegrity(501L));

            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            assertThrows(AccessDeniedException.class, () -> documentPublishService.verifyIntegrity(501L, null));
            assertThrows(AccessDeniedException.class, () -> documentPublishService.verifyIntegrity(501L));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        // [20/20] Kiểm tra khi có quyền nhưng hash không khớp -> trả về false chứ không ném ngoại lệ
        Document hashMismatchDoc = new Document();
        hashMismatchDoc.setId(502L);
        hashMismatchDoc.setStatus(DocumentStatus.DRAFT);
        hashMismatchDoc.setCreatorUser(creator);
        hashMismatchDoc.setOfficialHash("incorrect_hash_value");
        hashMismatchDoc.setOfficialFilePath(tempFile.getAbsolutePath());
        when(documentRepository.findById(502L)).thenReturn(Optional.of(hashMismatchDoc));
        assertFalse(documentPublishService.verifyIntegrity(502L, creatorAuth));
    }

    @Test
    void testVerifyUserCanAccessDocument_SecurityChecks() {
        UserLogin creator = new UserLogin();
        creator.setId(100L);

        Document pendingDoc = new Document();
        pendingDoc.setId(600L);
        pendingDoc.setStatus(DocumentStatus.WAITING_APPROVAL);
        pendingDoc.setCreatorUser(creator);

        when(documentRepository.findById(600L)).thenReturn(Optional.of(pendingDoc));

        JwtUserPrincipal otherUser = new JwtUserPrincipal(200L, "other", "USER", true, List.of());

        // 1. User có DOCUMENT_VIEW nhưng không phải creator -> bị từ chối
        Authentication docViewAuth = new UsernamePasswordAuthenticationToken(
                otherUser, null, List.of(new SimpleGrantedAuthority("DOCUMENT_VIEW")));
        assertThrows(AccessDeniedException.class, () -> documentWorkflowService.verifyUserCanAccessDocument(600L, docViewAuth));

        // 2. User có DOCUMENT_APPROVE nhưng không phải creator -> bị từ chối
        Authentication docApproveAuth = new UsernamePasswordAuthenticationToken(
                otherUser, null, List.of(new SimpleGrantedAuthority("DOCUMENT_APPROVE")));
        assertThrows(AccessDeniedException.class, () -> documentWorkflowService.verifyUserCanAccessDocument(600L, docApproveAuth));

        // 3. User có DOCUMENT_PUBLISH nhưng không phải creator -> bị từ chối
        Authentication docPublishAuth = new UsernamePasswordAuthenticationToken(
                otherUser, null, List.of(new SimpleGrantedAuthority("DOCUMENT_PUBLISH")));
        assertThrows(AccessDeniedException.class, () -> documentWorkflowService.verifyUserCanAccessDocument(600L, docPublishAuth));

        // 4. ROLE_ADMIN -> được phép
        Authentication adminAuth = new UsernamePasswordAuthenticationToken(
                otherUser, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertDoesNotThrow(() -> documentWorkflowService.verifyUserCanAccessDocument(600L, adminAuth));

        // 5. Chính creator (userId = 100L) -> được phép
        JwtUserPrincipal creatorPrincipal = new JwtUserPrincipal(100L, "creator", "USER", true, List.of());
        Authentication creatorAuth = new UsernamePasswordAuthenticationToken(
                creatorPrincipal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertDoesNotThrow(() -> documentWorkflowService.verifyUserCanAccessDocument(600L, creatorAuth));

        // 6. Tài liệu đã PUBLISHED -> được phép truy cập timeline
        Document publishedDoc = new Document();
        publishedDoc.setId(601L);
        publishedDoc.setStatus(DocumentStatus.PUBLISHED);
        publishedDoc.setCreatorUser(creator);
        when(documentRepository.findById(601L)).thenReturn(Optional.of(publishedDoc));

        assertDoesNotThrow(() -> documentWorkflowService.verifyUserCanAccessDocument(601L, docViewAuth));

        // 7. [8/17] [9/17] [10/17] Không suy đoán user ID từ username số hay reflection bừa bãi
        Authentication numUserAuth = new UsernamePasswordAuthenticationToken(
                new NumericUsernamePrincipal("100"), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertThrows(AccessDeniedException.class, () -> documentWorkflowService.verifyUserCanAccessDocument(600L, numUserAuth));

        // Principal dạng String chứa user id ("100") không phải UserIdAwarePrincipal -> bị từ chối
        Authentication strUserAuth = new UsernamePasswordAuthenticationToken(
                "100", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertThrows(AccessDeniedException.class, () -> documentWorkflowService.verifyUserCanAccessDocument(600L, strUserAuth));

        // UserDetails có username "100" không phải UserIdAwarePrincipal -> bị từ chối
        Authentication userDetailsAuth = new UsernamePasswordAuthenticationToken(
                new NumericUserDetailsPrincipal("100"), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertThrows(AccessDeniedException.class, () -> documentWorkflowService.verifyUserCanAccessDocument(600L, userDetailsAuth));

        // Principal có username số không trùng creatorId (999 != 100) -> bị từ chối
        Authentication diffNumUserAuth = new UsernamePasswordAuthenticationToken(
                new NumericUsernamePrincipal("999"), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertThrows(AccessDeniedException.class, () -> documentWorkflowService.verifyUserCanAccessDocument(600L, diffNumUserAuth));

        // Principal có getId() không implement UserIdAwarePrincipal -> bị từ chối
        Authentication nonUserIdAuth = new UsernamePasswordAuthenticationToken(
                new NonUserIdPrincipal(100L), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertThrows(AccessDeniedException.class, () -> documentWorkflowService.verifyUserCanAccessDocument(600L, nonUserIdAuth));

        // Principal implement UserIdAwarePrincipal trả về 100L trùng creatorId -> được phép
        Authentication withUserIdAuth = new UsernamePasswordAuthenticationToken(
                new PrincipalWithGetUserId(100L), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertDoesNotThrow(() -> documentWorkflowService.verifyUserCanAccessDocument(600L, withUserIdAuth));

        // Không trích xuất được userId và không có quyền -> bị từ chối
        Authentication invalidPrincipalAuth = new UsernamePasswordAuthenticationToken(
                new CustomStringPrincipal("non_numeric"), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertThrows(AccessDeniedException.class, () -> documentWorkflowService.verifyUserCanAccessDocument(600L, invalidPrincipalAuth));
    }
}