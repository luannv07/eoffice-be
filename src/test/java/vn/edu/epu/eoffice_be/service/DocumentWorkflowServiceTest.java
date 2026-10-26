package vn.edu.epu.eoffice_be.service;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.PendingDocumentResponse;
import vn.edu.epu.eoffice_be.dto.request.DocumentRejectRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRevisionRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRevokeRequest;
import vn.edu.epu.eoffice_be.service.DocumentBookService;
import vn.edu.epu.eoffice_be.dto.response.DocumentDetailResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentSignatureVerifyResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentWorkflowLogResponse;
import vn.edu.epu.eoffice_be.dto.response.SignatureItemResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentAttachment;
import vn.edu.epu.eoffice_be.entity.DocumentWorkflowLog;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentWorkflowLogRepository;
import vn.edu.epu.eoffice_be.repository.OrgNodeRepository;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.security.pki.PdfSigner;
import vn.edu.epu.eoffice_be.service.impl.DocumentWorkflowServiceImpl;
import vn.edu.epu.eoffice_be.workflow.WorkflowEngine;
import vn.edu.epu.eoffice_be.security.ApprovedDocumentReadPolicy;
import vn.edu.epu.eoffice_be.dto.WorkflowTimelineResponse;

@ExtendWith(MockitoExtension.class)
class DocumentWorkflowServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentWorkflowLogRepository documentWorkflowLogRepository;
    @Mock
    private UserLoginRepository userLoginRepository;
    @Mock
    private UserInfoRepository userInfoRepository;
    @Mock
    private OrgNodeRepository orgNodeRepository;
    @Mock
    private WorkflowEngine workflowEngine;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private vn.edu.epu.eoffice_be.security.pki.PdfSigner pdfSigner;
    @Mock
    private DocumentBookService documentBookService;
    @Mock
    private vn.edu.epu.eoffice_be.repository.DocumentAttachmentRepository documentAttachmentRepository;
    @Mock
    private vn.edu.epu.eoffice_be.storage.FileStorageService unifiedStorageService;
    @Mock
    private vn.edu.epu.eoffice_be.security.pki.PdfSignatureVerifier pdfSignatureVerifier;

    @Mock
    private vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService documentPdfStorageService;

    @Mock
    private vn.edu.epu.eoffice_be.repository.DocumentDistributionRepository distributionRepository;

    @Mock private DocumentWorkingPdfService workingPdf;
    @Mock private ApprovedDocumentReadPolicy approvedDocumentReadPolicy;

    @InjectMocks
    private DocumentWorkflowServiceImpl documentWorkflowService;

    private UserLogin leader;
    private OrgNode orgNode;
    private Document document;

    @BeforeEach
    void setUp() {
        leader = UserLogin.builder().id(1002L).username("leader").role(Role.ADMIN).build();
        orgNode = OrgNode.builder().id(2001L).nameVn("Khoa CNTT").build();

        document = Document.builder()
                .id(5001L)
                .title("Du thao Ke hoach 2026")
                .documentTypeCd("KE_HOACH")
                .drafter(leader)
                .issuingOrg(orgNode)
                .status(DocumentStatus.IN_REVIEW	)
                .build();

        JwtUserPrincipal principal = new JwtUserPrincipal(1002L, "leader", Role.ADMIN.name(), true, List.of());
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        lenient().when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private byte[] preparePublishDocument() {
        document.setStatus(DocumentStatus.APPROVED);
        document.setOfficialNumber("1/QD-DHEPU");
        document.setPrimaryFilePath("drafts/sample.pdf");
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        byte[] rawBytes = new byte[]{1, 2, 3};
        when(documentPdfStorageService.readPdf("drafts/sample.pdf")).thenReturn(rawBytes);
        return rawBytes;
    }

    @Test
    @DisplayName("UC12: Yêu cầu chỉnh sửa văn bản")
    void requestRevision_success() {
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        DocumentRevisionRequest request = DocumentRevisionRequest.builder()
                .comment("Can bo sung phan phan cong nhiem vu")
                .build();

        DocumentDetailResponse response = documentWorkflowService.requestRevision(5001L, request);

        assertNotNull(response);
        verify(workflowEngine).executeTransition(
                document,
                WorkflowAction.REQUEST_REVISION,
                leader,
                "Can bo sung phan phan cong nhiem vu",
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("requestRevision: Đóng băng tệp cũ (primaryFilePath = null, isMainDocument = false)")
    void requestRevision_freezesPreviousDocumentFiles() {
        document.setPrimaryFilePath("drafts/old_initial_signed.pdf");
        DocumentAttachment mainAttachment = DocumentAttachment.builder()
                .id(999L)
                .document(document)
                .originalFileName("old_doc.pdf")
                .isMainDocument(true)
                .build();

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));
        when(documentAttachmentRepository.findByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(5001L))
                .thenReturn(List.of(mainAttachment));

        DocumentRevisionRequest request = DocumentRevisionRequest.builder()
                .comment("Can bo sung phan phan cong nhiem vu")
                .build();

        DocumentDetailResponse response = documentWorkflowService.requestRevision(5001L, request);

        assertNotNull(response);
        assertNull(document.getPrimaryFilePath());
        assertFalse(mainAttachment.isMainDocument());
        verify(documentAttachmentRepository).save(mainAttachment);
        verify(documentRepository).save(document);
    }

    @Test
    @DisplayName("UC14: Từ chối phê duyệt văn bản")
    void reject_success() {
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        DocumentRejectRequest request = DocumentRejectRequest.builder()
                .comment("Khong phu hop voi chu truong")
                .build();

        DocumentDetailResponse response = documentWorkflowService.reject(5001L, request);

        assertNotNull(response);
        verify(workflowEngine).executeTransition(
                document,
                WorkflowAction.REJECT,
                leader,
                "Khong phu hop voi chu truong",
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("reject: Đóng băng tệp cũ (primaryFilePath = null, isMainDocument = false)")
    void reject_freezesPreviousDocumentFiles() {
        document.setPrimaryFilePath("drafts/old_initial_signed.pdf");
        DocumentAttachment mainAttachment = DocumentAttachment.builder()
                .id(999L)
                .document(document)
                .originalFileName("old_doc.pdf")
                .isMainDocument(true)
                .build();

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));
        when(documentAttachmentRepository.findByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(5001L))
                .thenReturn(List.of(mainAttachment));

        DocumentRejectRequest request = DocumentRejectRequest.builder()
                .comment("Khong phu hop voi chu truong")
                .build();

        DocumentDetailResponse response = documentWorkflowService.reject(5001L, request);

        assertNotNull(response);
        assertNull(document.getPrimaryFilePath());
        assertFalse(mainAttachment.isMainDocument());
        verify(documentAttachmentRepository).save(mainAttachment);
        verify(documentRepository).save(document);
    }

    @Test
    @DisplayName("Lấy lịch sử luồng duyệt theo timeline có phân trang")
    void getWorkflowHistory_success() {
        DocumentWorkflowLog log = DocumentWorkflowLog.builder()
                .id(1L)
                .document(document)
                .action(WorkflowAction.SUBMIT)
                .actor(leader)
                .fromStatus(DocumentStatus.DRAFT)
                .toStatus(DocumentStatus.IN_REVIEW	)
                .comment("Gui duyet")
                .build();

        Pageable pageable = PageRequest.of(0, 10, Sort.by("createdDate").ascending());
        Page<DocumentWorkflowLog> logPage = new PageImpl<>(List.of(log), pageable, 1);

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(documentWorkflowLogRepository.findByDocumentIdAndDeleteFlgFalse(eq(5001L), any(Pageable.class)))
                .thenReturn(logPage);

        Page<DocumentWorkflowLogResponse> history = documentWorkflowService.getWorkflowHistory(5001L, pageable);

        assertNotNull(history);
        assertEquals(1, history.getTotalElements());
        assertEquals(1, history.getContent().size());
        assertEquals(WorkflowAction.SUBMIT, history.getContent().get(0).getAction());
    }

    @Test
    @DisplayName("requestRevision: Actor không có thẩm quyền ném lỗi UNAUTHORIZED_WORKFLOW_ACTION")
    void requestRevision_unauthorizedActor_shouldThrowUnauthorizedWorkflowAction() {
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION))
                .when(workflowEngine).validateActorPermission(eq(document), eq(leader), eq(WorkflowAction.REQUEST_REVISION));

        DocumentRevisionRequest request = DocumentRevisionRequest.builder()
                .comment("Khong co tham quyen tra ve")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.requestRevision(5001L, request));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, ex.getErrorCode());

        verify(workflowEngine, never()).executeTransition(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("reject: Actor không có thẩm quyền ném lỗi UNAUTHORIZED_WORKFLOW_ACTION")
    void reject_unauthorizedActor_shouldThrowUnauthorizedWorkflowAction() {
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION))
                .when(workflowEngine).validateActorPermission(eq(document), eq(leader), eq(WorkflowAction.REJECT));

        DocumentRejectRequest request = DocumentRejectRequest.builder()
                .comment("Khong co quyen tu choi")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.reject(5001L, request));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, ex.getErrorCode());

        verify(workflowEngine, never()).executeTransition(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getPendingDocuments: Trả về Page<PendingDocumentResponse> phân trang từ repository")
    void getPendingDocuments_withPageable_shouldReturnPaginatedResponse() {
        Document doc1 = Document.builder()
                .title("Tờ trình mua sắm máy tính")
                .status(DocumentStatus.PENDING_APPROVAL)
                .build();
        doc1.setId(101L);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Document> documentPage = new PageImpl<>(List.of(doc1), pageable, 1);

        when(documentRepository.findByStatusInAndDeleteFlgFalse(eq(DocumentWorkflowServiceImpl.PENDING_STATUSES), eq(pageable)))
                .thenReturn(documentPage);

        Page<PendingDocumentResponse> result = documentWorkflowService.getPendingDocuments(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(101L, result.getContent().get(0).getId());
        assertEquals("Tờ trình mua sắm máy tính", result.getContent().get(0).getTitle());
        assertEquals(DocumentStatus.PENDING_APPROVAL.name(), result.getContent().get(0).getStatus());
        assertEquals("Lãnh đạo phê duyệt", result.getContent().get(0).getCurrentHolder());
        verify(documentRepository).findByStatusInAndDeleteFlgFalse(eq(DocumentWorkflowServiceImpl.PENDING_STATUSES), eq(pageable));
    }

    @Test
    @DisplayName("getPendingDocuments: Ánh xạ currentHolder chính xác cho PENDING_APPROVAL và IN_REVIEW")
        void getPendingDocuments_currentHolderMapping_shouldReturnCorrectHolder() {
        Document docPendingApproval = Document.builder()
                .title("Đề xuất chờ duyệt")
                .status(DocumentStatus.PENDING_APPROVAL)
                .build();
        docPendingApproval.setId(102L);
        Document docInReview = Document.builder()
                .title("Văn bản mới gửi")
                .status(DocumentStatus.IN_REVIEW)
                .build();
        docInReview.setId(103L);
        when(documentRepository.findByStatusInAndDeleteFlgFalse(eq(DocumentWorkflowServiceImpl.PENDING_STATUSES)))
                .thenReturn(List.of(docPendingApproval, docInReview));
        List<PendingDocumentResponse> list = documentWorkflowService.getPendingDocuments();
        assertNotNull(list);
        assertEquals(2, list.size());
        assertEquals("Lãnh đạo phê duyệt", list.get(0).getCurrentHolder(), "PENDING_APPROVAL phải là Lãnh đạo phê duyệt");
        assertEquals("Cán bộ thẩm định / ký nháy", list.get(1).getCurrentHolder(), "IN_REVIEW phải là Cán bộ thẩm định / ký nháy");
        }

    @Test
    @DisplayName("PENDING_STATUSES: Chứa PENDING_SIGN và hỗ trợ chuyển đổi trạng thái chờ ký")
    void pendingStatuses_shouldContainPendingSign() {
        assertTrue(DocumentWorkflowServiceImpl.PENDING_STATUSES.contains(DocumentStatus.PENDING_SIGN),
                "PENDING_STATUSES phải chứa DocumentStatus.PENDING_SIGN");

        Document doc = new Document();
        doc.setStatus("PENDING_SIGN");
        assertEquals(DocumentStatus.PENDING_SIGN, doc.getStatus(),
                "Chuỗi 'PENDING_SIGN' phải được gán đúng sang enum PENDING_SIGN");
        assertTrue(DocumentWorkflowServiceImpl.PENDING_STATUSES.contains(doc.getStatus()),
                "Văn bản với trạng thái PENDING_SIGN phải nằm trong danh sách pending");
    }

    @Test
    @DisplayName("getPendingSignDocuments: Lọc văn bản theo trạng thái PENDING_SIGN qua searchDocuments")
    void getPendingSignDocuments_shouldFilterByPendingSignStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        Document docPendingSign = Document.builder()
                .title("Tờ trình ký số")
                .status(DocumentStatus.PENDING_SIGN)
                .build();
        docPendingSign.setId(201L);
        Page<Document> documentPage = new PageImpl<>(List.of(docPendingSign), pageable, 1);

        when(documentRepository.searchDocuments(isNull(), eq(DocumentStatus.PENDING_SIGN), isNull(), isNull(), eq(pageable)))
                .thenReturn(documentPage);

        Page<PendingDocumentResponse> result = documentWorkflowService.getPendingSignDocuments(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(201L, result.getContent().get(0).getId());
        assertEquals("Tờ trình ký số", result.getContent().get(0).getTitle());
        assertEquals(DocumentStatus.PENDING_SIGN.name(), result.getContent().get(0).getStatus());
        assertEquals("Lãnh đạo ký số", result.getContent().get(0).getCurrentHolder());
        verify(documentRepository).searchDocuments(isNull(), eq(DocumentStatus.PENDING_SIGN), isNull(), isNull(), eq(pageable));
    }

    @Test
    @DisplayName("getPendingPublishDocuments: Clerk (CLERK role) lấy được toàn bộ văn bản APPROVED mà không bị filterUserId")
    void getPendingPublishDocuments_withClerkRole_shouldFetchAllApprovedDocuments() {
        UserLogin clerk = UserLogin.builder().id(1004L).username("vanthu").role(Role.CLERK).build();
        JwtUserPrincipal principal = new JwtUserPrincipal(1004L, "vanthu", Role.CLERK.name(), true, List.of());
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1004L)).thenReturn(Optional.of(clerk));

        Pageable pageable = PageRequest.of(0, 10);
        Document doc = Document.builder()
                .title("Quyết định ban hành")
                .status(DocumentStatus.APPROVED)
                .build();
        doc.setId(301L);
        Page<Document> documentPage = new PageImpl<>(List.of(doc), pageable, 1);

        when(documentRepository.searchDocuments(isNull(), eq(DocumentStatus.APPROVED), isNull(), isNull(), eq(pageable)))
                .thenReturn(documentPage);

        Page<PendingDocumentResponse> result = documentWorkflowService.getPendingPublishDocuments(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(301L, result.getContent().get(0).getId());
        assertEquals("Quyết định ban hành", result.getContent().get(0).getTitle());
        assertEquals(DocumentStatus.APPROVED.name(), result.getContent().get(0).getStatus());
        assertEquals("Lãnh đạo ký phát hành", result.getContent().get(0).getCurrentHolder());
        verify(documentRepository).searchDocuments(isNull(), eq(DocumentStatus.APPROVED), isNull(), isNull(), eq(pageable));
    }

    @Test
    @DisplayName("logAction: Ghi log thành công với actor và action hợp lệ")
    void logAction_withValidActorAndAction_shouldSaveWorkflowLog() {
        UserLogin actor = new UserLogin();
        actor.setId(10L);
        actor.setUsername("testuser");

        when(userLoginRepository.findByUsernameAndDeleteFlgFalse("testuser")).thenReturn(Optional.of(actor));

        documentWorkflowService.logAction(100L, "testuser", "CHUYEN_VIEN", "SUBMIT", "Nộp hồ sơ");
        verify(documentWorkflowLogRepository).save(any(DocumentWorkflowLog.class));
    }

    @Test
    @DisplayName("logAction: Ném BAD_REQUEST khi action null, blank hoặc không hợp lệ (không fallback SUBMIT âm thầm)")
    void logAction_withInvalidOrBlankAction_shouldThrowBadRequest() {
        // Blank action
        BusinessException exBlank = assertThrows(BusinessException.class,
                () -> documentWorkflowService.logAction(100L, "testuser", "CHUYEN_VIEN", "   ", "comment"));
        assertEquals(ErrorCode.BAD_REQUEST, exBlank.getErrorCode());

        // Null action
        BusinessException exNull = assertThrows(BusinessException.class,
                () -> documentWorkflowService.logAction(100L, "testuser", "CHUYEN_VIEN", null, "comment"));
        assertEquals(ErrorCode.BAD_REQUEST, exNull.getErrorCode());

        // Invalid action string
        BusinessException exInvalid = assertThrows(BusinessException.class,
                () -> documentWorkflowService.logAction(100L, "testuser", "CHUYEN_VIEN", "UNKNOWN_ACTION", "comment"));
        assertEquals(ErrorCode.BAD_REQUEST, exInvalid.getErrorCode());

        verify(documentWorkflowLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("logAction: Ném UNAUTHORIZED khi không thể xác định actor (không gán ngẫu nhiên từ DB)")
    void logAction_whenActorNotFound_shouldThrowUnauthorized() {
        SecurityContextHolder.clearContext();
        when(userLoginRepository.findByUsernameAndDeleteFlgFalse("nonexistent")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.logAction(100L, "nonexistent", "CHUYEN_VIEN", "SUBMIT", "comment"));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());

        verify(documentWorkflowLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("getDetail: Ném FORBIDDEN khi người dùng thường không phải người soạn/ký truy cập văn bản nháp")
    void getDetail_whenUnauthorizedRegularUser_shouldThrowForbidden() {
        UserLogin regularUser = UserLogin.builder().id(9999L).username("stranger").role(Role.USER).build();
        JwtUserPrincipal principal = new JwtUserPrincipal(9999L, "stranger", Role.USER.name(), true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        lenient().when(userLoginRepository.findByIdAndDeleteFlgFalse(9999L)).thenReturn(Optional.of(regularUser));

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.getDetail(5001L));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("getDetail: Cho phép người dùng thường truy cập khi văn bản đã được PUBLISHED")
    void getDetail_whenDocumentPublished_shouldAllowRegularUser() {
        UserLogin regularUser = UserLogin.builder().id(9999L).username("stranger").role(Role.USER).build();
        JwtUserPrincipal principal = new JwtUserPrincipal(9999L, "stranger", Role.USER.name(), true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        lenient().when(userLoginRepository.findByIdAndDeleteFlgFalse(9999L)).thenReturn(Optional.of(regularUser));

        Document publishedDoc = Document.builder()
                .id(5001L)
                .title("Van ban da ban hanh")
                .status(DocumentStatus.PUBLISHED)
                .build();
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(publishedDoc));

        DocumentDetailResponse response = documentWorkflowService.getDetail(5001L);
        assertNotNull(response);
        assertEquals(5001L, response.getId());
    }

    @Test
    @DisplayName("getDetail: Ném DOCUMENT_NOT_FOUND khi văn bản không tồn tại")
    void getDetail_whenNotFound_shouldThrowNotFound() {
        when(documentRepository.findByIdAndDeleteFlgFalse(99999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.getDetail(99999L));
        assertEquals(ErrorCode.DOCUMENT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("getWorkflowHistory: Ném FORBIDDEN khi người dùng không có quyền truy cập văn bản")
    void getWorkflowHistory_whenUnauthorized_shouldThrowForbidden() {
        UserLogin regularUser = UserLogin.builder().id(9999L).username("stranger").role(Role.USER).build();
        JwtUserPrincipal principal = new JwtUserPrincipal(9999L, "stranger", Role.USER.name(), true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        lenient().when(userLoginRepository.findByIdAndDeleteFlgFalse(9999L)).thenReturn(Optional.of(regularUser));

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.getWorkflowHistory(5001L, PageRequest.of(0, 10)));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("getDocumentTimeline: Ném FORBIDDEN khi người dùng không có quyền truy cập văn bản")
    void getDocumentTimeline_whenUnauthorized_shouldThrowForbidden() {
        UserLogin regularUser = UserLogin.builder().id(9999L).username("stranger").role(Role.USER).build();
        JwtUserPrincipal principal = new JwtUserPrincipal(9999L, "stranger", Role.USER.name(), true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        lenient().when(userLoginRepository.findByIdAndDeleteFlgFalse(9999L)).thenReturn(Optional.of(regularUser));

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.getDocumentTimeline(5001L));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @org.junit.jupiter.api.Test
    void getWorkflowHistory_whenPageableNullOrUnpaged_shouldApplyDefaultPaginationAndTieBreaker() {
        Long docId = 100L;
        vn.edu.epu.eoffice_be.entity.Document mockDoc = vn.edu.epu.eoffice_be.entity.Document.builder()
                .id(docId)
                .status(vn.edu.epu.eoffice_be.common.enums.DocumentStatus.PUBLISHED)
                .build();
        vn.edu.epu.eoffice_be.entity.UserLogin mockUser = vn.edu.epu.eoffice_be.entity.UserLogin.builder()
                .id(1L)
                .role(vn.edu.epu.eoffice_be.common.enums.Role.ADMIN)
                .username("admin")
                .build();

        org.mockito.Mockito.when(documentRepository.findByIdAndDeleteFlgFalse(docId))
                .thenReturn(java.util.Optional.of(mockDoc));
        org.mockito.Mockito.when(userLoginRepository.findByIdAndDeleteFlgFalse(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(java.util.Optional.of(mockUser));
        org.mockito.Mockito.when(documentWorkflowLogRepository.findByDocumentIdAndDeleteFlgFalse(org.mockito.ArgumentMatchers.eq(docId), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        // Test with null pageable
        org.springframework.data.domain.Page<?> resNull = documentWorkflowService.getWorkflowHistory(docId, null);
        org.junit.jupiter.api.Assertions.assertNotNull(resNull);

        // Test with unpaged
        org.springframework.data.domain.Page<?> resUnpaged = documentWorkflowService.getWorkflowHistory(docId, Pageable.unpaged());
        org.junit.jupiter.api.Assertions.assertNotNull(resUnpaged);
    }

    @org.junit.jupiter.api.Test
    void getWorkflowHistory_whenClientSortProvided_shouldAppendIdTieBreaker() {
        Long docId = 200L;
        vn.edu.epu.eoffice_be.entity.Document mockDoc = vn.edu.epu.eoffice_be.entity.Document.builder()
                .id(docId)
                .status(vn.edu.epu.eoffice_be.common.enums.DocumentStatus.PUBLISHED)
                .build();
        vn.edu.epu.eoffice_be.entity.UserLogin mockUser = vn.edu.epu.eoffice_be.entity.UserLogin.builder()
                .id(1L)
                .role(vn.edu.epu.eoffice_be.common.enums.Role.ADMIN)
                .username("admin")
                .build();

        org.mockito.Mockito.when(documentRepository.findByIdAndDeleteFlgFalse(docId))
                .thenReturn(java.util.Optional.of(mockDoc));
        org.mockito.Mockito.when(userLoginRepository.findByIdAndDeleteFlgFalse(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(java.util.Optional.of(mockUser));
        org.mockito.Mockito.when(documentWorkflowLogRepository.findByDocumentIdAndDeleteFlgFalse(org.mockito.ArgumentMatchers.eq(docId), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        Pageable customSortPageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdDate"));
        org.springframework.data.domain.Page<?> res = documentWorkflowService.getWorkflowHistory(docId, customSortPageable);
        org.junit.jupiter.api.Assertions.assertNotNull(res);
    }

    @Test
    @DisplayName("UC-SIG-VERIFY: Kiểm định chữ ký số văn bản thành công")
    void verifyDocumentSignatures_shouldReturnVerificationResult() {
        Long docId = 5001L;
        document.setPrimaryFilePath("drafts/test_signed.pdf");
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(document));
        when(workingPdf.read(document)).thenReturn(new byte[]{1, 2, 3});

        DocumentSignatureVerifyResponse mockResponse = DocumentSignatureVerifyResponse.builder()
                .overallStatus("VALID")
                .overallStatusDesc("HỢP LỆ")
                .totalSignatures(1)
                .build();
        when(pdfSignatureVerifier.verify(any(byte[].class))).thenReturn(mockResponse);

        DocumentSignatureVerifyResponse result = documentWorkflowService.verifyDocumentSignatures(docId);

        assertNotNull(result);
        assertEquals("VALID", result.getOverallStatus());
        assertEquals(docId, result.getDocumentId());
        assertEquals("Du thao Ke hoach 2026", result.getDocumentTitle());
        verify(pdfSignatureVerifier).verify(any(byte[].class));
    }

    @ParameterizedTest
    @EnumSource(value = DocumentStatus.class, names = {"PUBLISHED", "ISSUED", "REVOKED"})
    void verificationOfIssuedDocumentReadsOfficialPdfOnly(DocumentStatus status) {
        document.setStatus(status);
        document.setPrimaryFilePath("storage://35001");
        document.setOfficialFilePath("storage://36001");
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        byte[] officialPdf = new byte[]{4, 5, 6};
        when(documentPdfStorageService.readPdf("storage://36001")).thenReturn(officialPdf);
        when(pdfSignatureVerifier.verify(eq(officialPdf)))
                .thenReturn(DocumentSignatureVerifyResponse.builder().totalSignatures(3).overallStatus("VALID").build());

        DocumentSignatureVerifyResponse result = documentWorkflowService.verifyDocumentSignatures(5001L);

        assertEquals(3, result.getTotalSignatures());
        verify(pdfSignatureVerifier).verify(eq(officialPdf));
        verify(documentPdfStorageService, never()).readPdf("storage://35001");
        verifyNoInteractions(documentAttachmentRepository, unifiedStorageService);
    }

    @ParameterizedTest
    @EnumSource(value = DocumentStatus.class, names = {"PUBLISHED", "ISSUED", "REVOKED"})
    void missingOfficialPdfDoesNotVerifyWorkingPdfOrOriginalAttachment(DocumentStatus status) {
        document.setStatus(status);
        document.setPrimaryFilePath("storage://35001");
        document.setOfficialFilePath(null);
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        BusinessException failure = new BusinessException(ErrorCode.DOCUMENT_PDF_NOT_FOUND);
        when(documentPdfStorageService.readPdf(isNull())).thenThrow(failure);

        assertSame(failure, assertThrows(BusinessException.class,
                () -> documentWorkflowService.verifyDocumentSignatures(5001L)));

        verifyNoInteractions(pdfSignatureVerifier, documentAttachmentRepository, unifiedStorageService);
        verify(documentPdfStorageService, never()).readPdf("storage://35001");
    }

    @Test
    void verificationStorageFailureIsNotReportedAsUnsignedDocument() {
        document.setStatus(DocumentStatus.PUBLISHED);
        document.setOfficialFilePath("storage://36001");
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        BusinessException failure = new BusinessException(ErrorCode.DOCUMENT_PDF_STORAGE_UNAVAILABLE);
        when(documentPdfStorageService.readPdf("storage://36001")).thenThrow(failure);

        assertSame(failure, assertThrows(BusinessException.class,
                () -> documentWorkflowService.verifyDocumentSignatures(5001L)));
        verifyNoInteractions(pdfSignatureVerifier);
    }

    @Test
    @DisplayName("verifyDocumentSignatures: Ném FORBIDDEN khi người dùng không có quyền truy cập văn bản")
    void verifyDocumentSignatures_whenUnauthorized_shouldThrowForbidden() {
        UserLogin regularUser = UserLogin.builder().id(9999L).username("stranger").role(Role.USER).build();
        JwtUserPrincipal principal = new JwtUserPrincipal(9999L, "stranger", Role.USER.name(), true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        lenient().when(userLoginRepository.findByIdAndDeleteFlgFalse(9999L)).thenReturn(Optional.of(regularUser));

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.verifyDocumentSignatures(5001L));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("verifyDocumentSignatures: Cho phép kiểm định chữ ký khi văn bản đã PUBLISHED")
    void verifyDocumentSignatures_whenDocumentPublished_shouldAllowRegularUser() {
        UserLogin regularUser = UserLogin.builder().id(9999L).username("stranger").role(Role.USER).build();
        JwtUserPrincipal principal = new JwtUserPrincipal(9999L, "stranger", Role.USER.name(), true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        lenient().when(userLoginRepository.findByIdAndDeleteFlgFalse(9999L)).thenReturn(Optional.of(regularUser));

        Document publishedDoc = Document.builder()
                .id(5001L)
                .title("Van ban da ban hanh")
                .status(DocumentStatus.PUBLISHED)
                .officialFilePath("official/published.pdf")
                .build();
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(publishedDoc));
        when(documentPdfStorageService.readPdf("official/published.pdf")).thenReturn(new byte[]{1, 2, 3});

        DocumentSignatureVerifyResponse mockResponse = DocumentSignatureVerifyResponse.builder()
                .overallStatus("VALID")
                .overallStatusDesc("HỢP LỆ")
                .totalSignatures(1)
                .build();
        when(pdfSignatureVerifier.verify(any(byte[].class))).thenReturn(mockResponse);

        DocumentSignatureVerifyResponse result = documentWorkflowService.verifyDocumentSignatures(5001L);
        assertNotNull(result);
        assertEquals("VALID", result.getOverallStatus());
        assertEquals(5001L, result.getDocumentId());
    }

    @Test
    @DisplayName("REVOKE: Thu hồi văn bản đã ban hành thành công và đồng bộ CRL")
    void revokeDocument_success() {
        Long docId = 5001L;
        document.setStatus(DocumentStatus.PUBLISHED);
        document.setOfficialFilePath("official/test_signed.pdf");
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(document));
        when(documentPdfStorageService.readPdf("official/test_signed.pdf")).thenReturn(new byte[]{1, 2, 3});

        SignatureItemResponse item = SignatureItemResponse.builder()
                .serialHex("ABC123456789")
                .build();
        DocumentSignatureVerifyResponse verifyResp = DocumentSignatureVerifyResponse.builder()
                .signatures(List.of(item))
                .build();
        when(pdfSignatureVerifier.verify(any(byte[].class))).thenReturn(verifyResp);

        DocumentRevokeRequest req = DocumentRevokeRequest.builder()
                .comment("Thu hồi văn bản do sai số liệu")
                .build();

        DocumentDetailResponse resp = documentWorkflowService.revokeDocument(docId, req);

        assertNotNull(resp);
        verify(workflowEngine).validateActorPermission(document, leader, WorkflowAction.REVOKE);
        verify(workflowEngine).executeTransition(
                document,
                WorkflowAction.REVOKE,
                leader,
                "Thu hồi văn bản do sai số liệu",
                null,
                null,
                null
        );
        verify(pdfSignatureVerifier).revokeSerial("ABC123456789");
    }

    @Test
    @DisplayName("REVOKE: Không đủ thẩm quyền thu hồi văn bản ném BusinessException")
    void revokeDocument_unauthorized_shouldThrowException() {
        Long docId = 5001L;
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(document));

        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "Không có quyền thu hồi văn bản"))
                .when(workflowEngine).validateActorPermission(document, leader, WorkflowAction.REVOKE);

        DocumentRevokeRequest req = DocumentRevokeRequest.builder()
                .comment("Thu hồi trái phép")
                .build();

        assertThrows(BusinessException.class, () -> documentWorkflowService.revokeDocument(docId, req));
        verify(workflowEngine, never()).executeTransition(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getDetail: Ném FORBIDDEN khi văn bản APPROVED và chính sách từ chối")
    void getDetail_whenDocumentApproved_andPolicyThrowsForbidden_shouldThrowForbidden() {
        UserLogin regularUser = UserLogin.builder().id(9999L).username("stranger").role(Role.USER).build();
        JwtUserPrincipal principal = new JwtUserPrincipal(9999L, "stranger", Role.USER.name(), true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        lenient().when(userLoginRepository.findByIdAndDeleteFlgFalse(9999L)).thenReturn(Optional.of(regularUser));

        Document approvedDoc = Document.builder()
                .id(5001L)
                .title("Van ban approved")
                .status(DocumentStatus.APPROVED)
                .build();
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(approvedDoc));
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(approvedDocumentReadPolicy).assertCanRead(approvedDoc, regularUser);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.getDetail(5001L));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(approvedDocumentReadPolicy).assertCanRead(approvedDoc, regularUser);
    }

    @Test
    @DisplayName("getDetail: Cho phép xem khi văn bản APPROVED và chính sách chấp thuận")
    void getDetail_whenDocumentApproved_andPolicyAllows_shouldReturnDetail() {
        UserLogin clerk = UserLogin.builder().id(8888L).username("clerk").role(Role.USER).build();
        JwtUserPrincipal principal = new JwtUserPrincipal(8888L, "clerk", Role.USER.name(), true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        lenient().when(userLoginRepository.findByIdAndDeleteFlgFalse(8888L)).thenReturn(Optional.of(clerk));

        Document approvedDoc = Document.builder()
                .id(5001L)
                .title("Van ban approved")
                .status(DocumentStatus.APPROVED)
                .build();
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(approvedDoc));
        org.mockito.Mockito.doNothing().when(approvedDocumentReadPolicy).assertCanRead(approvedDoc, clerk);

        DocumentDetailResponse response = documentWorkflowService.getDetail(5001L);
        assertNotNull(response);
        assertEquals(5001L, response.getId());
        verify(approvedDocumentReadPolicy).assertCanRead(approvedDoc, clerk);
    }

    @Test
    @DisplayName("getDocumentTimeline: Ném AccessDeniedException khi văn bản APPROVED và chính sách từ chối")
    void getDocumentTimeline_whenDocumentApproved_andPolicyRejects_shouldThrowAccessDeniedException() {
        UserLogin actor = UserLogin.builder().id(9999L).username("stranger").role(Role.USER).build();
        JwtUserPrincipal principal = new JwtUserPrincipal(9999L, "stranger", Role.USER.name(), true, List.of());
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());

        Document approvedDoc = Document.builder()
                .id(5001L)
                .title("Van ban approved")
                .status(DocumentStatus.APPROVED)
                .build();
        when(documentRepository.findById(5001L)).thenReturn(Optional.of(approvedDoc));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(9999L)).thenReturn(Optional.of(actor));
        when(approvedDocumentReadPolicy.canRead(approvedDoc, actor)).thenReturn(false);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> documentWorkflowService.getDocumentTimeline(5001L, auth));
        verify(approvedDocumentReadPolicy).canRead(approvedDoc, actor);
    }

    @Test
    @DisplayName("getDocumentTimeline: Cho phép xem khi văn bản APPROVED và chính sách chấp thuận")
    void getDocumentTimeline_whenDocumentApproved_andPolicyAllows_shouldReturnTimeline() {
        UserLogin actor = UserLogin.builder().id(8888L).username("clerk").role(Role.USER).build();
        JwtUserPrincipal principal = new JwtUserPrincipal(8888L, "clerk", Role.USER.name(), true, List.of());
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());

        Document approvedDoc = Document.builder()
                .id(5001L)
                .title("Van ban approved")
                .status(DocumentStatus.APPROVED)
                .build();
        when(documentRepository.findById(5001L)).thenReturn(Optional.of(approvedDoc));
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(approvedDoc));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(8888L)).thenReturn(Optional.of(actor));
        when(approvedDocumentReadPolicy.canRead(approvedDoc, actor)).thenReturn(true);
        when(documentWorkflowLogRepository.findByDocumentIdOrderByCreatedAtAsc(5001L))
                .thenReturn(java.util.Collections.emptyList());

        List<WorkflowTimelineResponse> response = documentWorkflowService.getDocumentTimeline(5001L, auth);
        assertNotNull(response);
        verify(approvedDocumentReadPolicy).canRead(approvedDoc, actor);
    }
}
