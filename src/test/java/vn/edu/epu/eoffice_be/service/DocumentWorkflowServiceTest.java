package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.PendingDocumentResponse;
import vn.edu.epu.eoffice_be.dto.StampInfo;
import vn.edu.epu.eoffice_be.dto.request.DocumentApproveRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentInitialSignRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentPublishRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRejectRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRevisionRequest;
import vn.edu.epu.eoffice_be.dto.response.DocumentDetailResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentWorkflowLogResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentWorkflowLog;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentWorkflowLogRepository;
import vn.edu.epu.eoffice_be.repository.OrgNodeRepository;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.service.impl.DocumentWorkflowServiceImpl;
import vn.edu.epu.eoffice_be.workflow.WorkflowEngine;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private PdfProcessingService pdfProcessingService;

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
                .status(DocumentStatus.SUBMITTED)
                .build();

        JwtUserPrincipal principal = new JwtUserPrincipal(1002L, "leader", Role.ADMIN.name(), true, List.of());
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("UC12: Duyệt văn bản với Bút phê lãnh đạo và đơn vị chủ trì")
    void approve_success() {
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));
        when(orgNodeRepository.findByIdAndDeleteFlgFalse(2001L)).thenReturn(Optional.of(orgNode));

        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .comment("Dong y thong qua")
                .leadershipInstruction("Giao Khoa CNTT chu tri")
                .assignedOrgNodeId(2001L)
                .build();

        DocumentDetailResponse response = documentWorkflowService.approve(5001L, request);

        assertNotNull(response);
        verify(workflowEngine).executeTransition(
                document,
                WorkflowAction.REVIEW_APPROVE,
                leader,
                "Dong y thong qua",
                "Giao Khoa CNTT chu tri",
                orgNode,
                null
        );
    }

    @Test
    @DisplayName("UC12: Duyệt văn bản ở trạng thái LEADER_REVIEW thì action là LEADER_APPROVE")
    void approve_whenLeaderReview_shouldUseLeaderApprove() {
        document.setStatus(DocumentStatus.LEADER_REVIEW);
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .comment("Lanh dao dong y")
                .build();

        DocumentDetailResponse response = documentWorkflowService.approve(5001L, request);

        assertNotNull(response);
        verify(workflowEngine).executeTransition(
                document,
                WorkflowAction.LEADER_APPROVE,
                leader,
                "Lanh dao dong y",
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("UC12: Duyệt văn bản thất bại khi trạng thái không hợp lệ (ví dụ DRAFT)")
    void approve_whenInvalidStatus_shouldThrowBusinessException() {
        document.setStatus(DocumentStatus.DRAFT);
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .comment("Dong y")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.approve(5001L, request));
        assertEquals(ErrorCode.INVALID_WORKFLOW_STATE, ex.getErrorCode());
    }

    @Test
    @DisplayName("UC12: Duyệt văn bản thất bại khi không tìm thấy đơn vị chủ trì được gán")
    void approve_whenAssignedOrgNotFound_shouldThrowException() {
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));
        when(orgNodeRepository.findByIdAndDeleteFlgFalse(9999L)).thenReturn(Optional.empty());

        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .comment("Dong y")
                .assignedOrgNodeId(9999L)
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.approve(5001L, request));
        assertEquals(ErrorCode.ORG_NODE_NOT_FOUND, ex.getErrorCode());
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
    @DisplayName("UC13: Ký nháy văn bản và lưu mã băm SHA-256")
    void initialSign_success() {
        document.setPrimaryFilePath("drafts/sample.pdf");
        byte[] mockBytes = new byte[]{1, 2, 3};

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));
        when(fileStorageService.loadFileBytes("drafts/sample.pdf")).thenReturn(mockBytes);
        when(pdfProcessingService.applyInitialSignStamp(any(byte[].class), any(StampInfo.class))).thenReturn(mockBytes);
        when(fileStorageService.storeFile(any(byte[].class), anyString(), anyString())).thenReturn("drafts/stamped.pdf");
        when(fileStorageService.computeSha256(any(byte[].class))).thenReturn("mock_sha256_hash");

        DocumentInitialSignRequest request = DocumentInitialSignRequest.builder()
                .comment("Da ra soat the thuc")
                .build();

        DocumentDetailResponse response = documentWorkflowService.initialSign(5001L, request);

        assertNotNull(response);
        assertEquals("drafts/stamped.pdf", document.getPrimaryFilePath());
        assertEquals("/documents/5001/files/primary", response.getPrimaryFileUrl());
        assertNull(response.getOfficialFileUrl());
        verify(workflowEngine).executeTransition(
                document,
                WorkflowAction.INITIAL_SIGN,
                leader,
                "Da ra soat the thuc",
                null,
                null,
                "mock_sha256_hash"
        );
    }

    @Test
    @DisplayName("UC14: Ký số phát hành PKI X.509 và cấp số hiệu văn bản")
    void signAndPublish_success() {
        document.setPrimaryFilePath("drafts/sample.pdf");
        byte[] mockBytes = new byte[]{1, 2, 3};

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));
        when(fileStorageService.loadFileBytes("drafts/sample.pdf")).thenReturn(mockBytes);
        when(pdfProcessingService.signAndCertifyPdf(any(byte[].class), anyString(), anyString())).thenReturn(mockBytes);
        when(fileStorageService.storeFile(any(byte[].class), anyString(), anyString())).thenReturn("official/doc_5001.pdf");
        when(fileStorageService.computeSha256(any(byte[].class))).thenReturn("official_sha256_hash");

        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .officialNumber("125/QD-DHEPU")
                .comment("Ky ban hanh chinh thuc")
                .build();

        DocumentDetailResponse response = documentWorkflowService.signAndPublish(5001L, request);

        assertNotNull(response);
        assertEquals("125/QD-DHEPU", document.getOfficialNumber());
        assertEquals("official/doc_5001.pdf", document.getOfficialFilePath());
        assertEquals("official_sha256_hash", document.getOfficialHash());
        assertEquals("/documents/5001/files/primary", response.getPrimaryFileUrl());
        assertEquals("/documents/5001/files/official", response.getOfficialFileUrl());

        verify(workflowEngine).executeTransition(
                document,
                WorkflowAction.SIGN_AND_PUBLISH,
                leader,
                "Ky ban hanh chinh thuc",
                null,
                null,
                "official_sha256_hash"
        );
    }

    @Test
    @DisplayName("initialSign: nếu workflow transition thất bại thì phải xóa file đã lưu trong storage")
    void initialSign_transitionFails_shouldCleanUpStoredFile() {
        document.setPrimaryFilePath("drafts/sample.pdf");
        byte[] mockBytes = new byte[]{1, 2, 3};

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));
        when(fileStorageService.loadFileBytes("drafts/sample.pdf")).thenReturn(mockBytes);
        when(pdfProcessingService.applyInitialSignStamp(any(byte[].class), any(StampInfo.class))).thenReturn(mockBytes);
        when(fileStorageService.storeFile(any(byte[].class), anyString(), anyString())).thenReturn("drafts/stamped_failed.pdf");
        when(fileStorageService.computeSha256(any(byte[].class))).thenReturn("mock_sha256_hash");

        doThrow(new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE))
                .when(workflowEngine).executeTransition(
                        any(), any(), any(), any(), any(), any(), any()
                );

        DocumentInitialSignRequest request = DocumentInitialSignRequest.builder()
                .comment("Da ra soat the thuc")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.initialSign(5001L, request));
        assertEquals(ErrorCode.INVALID_WORKFLOW_STATE, ex.getErrorCode());

        verify(fileStorageService).deleteFile("drafts/stamped_failed.pdf");
    }

    @Test
    @DisplayName("signAndPublish: nếu workflow transition thất bại thì phải xóa file official đã lưu trong storage")
    void signAndPublish_transitionFails_shouldCleanUpStoredFile() {
        document.setPrimaryFilePath("drafts/sample.pdf");
        byte[] mockBytes = new byte[]{1, 2, 3};

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));
        when(fileStorageService.loadFileBytes("drafts/sample.pdf")).thenReturn(mockBytes);
        when(pdfProcessingService.signAndCertifyPdf(any(byte[].class), anyString(), anyString())).thenReturn(mockBytes);
        when(fileStorageService.storeFile(any(byte[].class), anyString(), anyString())).thenReturn("official/doc_failed.pdf");
        when(fileStorageService.computeSha256(any(byte[].class))).thenReturn("official_sha256_hash");

        doThrow(new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE))
                .when(workflowEngine).executeTransition(
                        any(), any(), any(), any(), any(), any(), any()
                );

        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .officialNumber("125/QD-DHEPU")
                .comment("Ky ban hanh chinh thuc")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.signAndPublish(5001L, request));
        assertEquals(ErrorCode.INVALID_WORKFLOW_STATE, ex.getErrorCode());

        verify(fileStorageService).deleteFile("official/doc_failed.pdf");
    }

    @Test
    @DisplayName("UC13: Ký nháy thất bại khi văn bản chưa có file đính kèm chính")
    void initialSign_whenPrimaryFileMissing_shouldThrowException() {
        document.setPrimaryFilePath(null);

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        DocumentInitialSignRequest request = DocumentInitialSignRequest.builder()
                .comment("Da ra soat the thuc")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.initialSign(5001L, request));
        assertEquals(ErrorCode.DOCUMENT_PRIMARY_FILE_MISSING, ex.getErrorCode());
    }

    @Test
    @DisplayName("UC14: Ký phát hành thất bại khi văn bản chưa có file đính kèm chính")
    void signAndPublish_whenPrimaryFileMissing_shouldThrowException() {
        document.setPrimaryFilePath(null);

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .officialNumber("125/QD-DHEPU")
                .comment("Ky ban hanh")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.signAndPublish(5001L, request));
        assertEquals(ErrorCode.DOCUMENT_PRIMARY_FILE_MISSING, ex.getErrorCode());
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
    @DisplayName("Chưa xác thực ném lỗi BusinessException UNAUTHORIZED")
    void approve_unauthenticated_shouldThrowException() {
        SecurityContextHolder.clearContext();

        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .comment("Dong y thong qua")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.approve(5001L, request));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
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
                .toStatus(DocumentStatus.SUBMITTED)
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
    @DisplayName("approve: Actor không có thẩm quyền ném lỗi UNAUTHORIZED_WORKFLOW_ACTION")
    void approve_unauthorizedActor_shouldThrowUnauthorizedWorkflowAction() {
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION))
                .when(workflowEngine).validateActorPermission(eq(document), eq(leader), eq(WorkflowAction.REVIEW_APPROVE));

        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .comment("Khong co tham quyen")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.approve(5001L, request));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, ex.getErrorCode());

        verify(workflowEngine, never()).executeTransition(any(), any(), any(), any(), any(), any(), any());
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
    @DisplayName("initialSign: Chặn actor không có quyền ngay từ đầu và bỏ qua xử lý PDF/lưu file")
    void initialSign_unauthorizedActor_shouldThrowUnauthorizedAndSkipPdfProcessing() {
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION))
                .when(workflowEngine).validateActorPermission(eq(document), eq(leader), eq(WorkflowAction.INITIAL_SIGN));

        DocumentInitialSignRequest request = DocumentInitialSignRequest.builder()
                .comment("Khong co tham quyen ky nhay")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.initialSign(5001L, request));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, ex.getErrorCode());

        verify(pdfProcessingService, never()).applyInitialSignStamp(any(), any());
        verify(fileStorageService, never()).storeFile(any(), any(), any());
    }

    @Test
    @DisplayName("signAndPublish: Chặn actor không có quyền ngay từ đầu và bỏ qua ký số PKI/lưu file")
    void signAndPublish_unauthorizedActor_shouldThrowUnauthorizedAndSkipSigning() {
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION))
                .when(workflowEngine).validateActorPermission(eq(document), eq(leader), eq(WorkflowAction.SIGN_AND_PUBLISH));

        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .officialNumber("999/QD-TEST")
                .comment("Khong co tham quyen ban hanh")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.signAndPublish(5001L, request));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, ex.getErrorCode());

        verify(pdfProcessingService, never()).signAndCertifyPdf(any(), any(), any());
        verify(fileStorageService, never()).storeFile(any(), any(), any());
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

        when(documentRepository.findByStatusIn(any(), eq(pageable))).thenReturn(documentPage);

        Page<PendingDocumentResponse> result = documentWorkflowService.getPendingDocuments(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(101L, result.getContent().get(0).getId());
        assertEquals("Tờ trình mua sắm máy tính", result.getContent().get(0).getTitle());
        assertEquals(DocumentStatus.PENDING_APPROVAL.name(), result.getContent().get(0).getStatus());
        verify(documentRepository).findByStatusIn(any(), eq(pageable));
    }
}
