package vn.edu.epu.eoffice_be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import vn.edu.epu.eoffice_be.dto.request.AssignDocumentNumberRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentApproveRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentInitialSignRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentPublishRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRejectRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRevisionRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRevokeRequest;
import vn.edu.epu.eoffice_be.dto.response.AssignDocumentNumberResponse;
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
    @DisplayName("UC12: Duyệt chuyển tiếp ở trạng thái IN_REVIEW tự động ký nháy thẩm định khi có tệp PDF")
    void approve_whenInReviewWithPdf_shouldAutoInitialSign() {
        document.setStatus(DocumentStatus.IN_REVIEW);
        document.setPrimaryFilePath("drafts/doc_5001.pdf");
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        byte[] rawPdfBytes = "%PDF-1.5 test".getBytes();
        byte[] signedPdfBytes = "%PDF-1.5 signed".getBytes();
        when(fileStorageService.loadFileBytes("drafts/doc_5001.pdf")).thenReturn(rawPdfBytes);

        PdfSigner.SignResult mockSignResult = new PdfSigner.SignResult(
                "1002",
                "leader",
                "123456",
                "hash_sig_reviewer_2",
                new java.util.Date(),
                "sig_user_1002",
                null,
                signedPdfBytes
        );
        when(pdfSigner.signBytes(any(byte[].class), anyString(), anyString(), anyString())).thenReturn(mockSignResult);
        when(fileStorageService.storeFile(any(byte[].class), anyString(), anyString())).thenReturn("drafts/initial_signed_5001.pdf");

        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .comment("Dong y thong qua")
                .build();

        DocumentDetailResponse response = documentWorkflowService.approve(5001L, request);

        assertNotNull(response);
        assertEquals("drafts/initial_signed_5001.pdf", document.getPrimaryFilePath());
        verify(pdfSigner).signBytes(any(byte[].class), anyString(), anyString(), anyString());
        verify(workflowEngine).executeTransition(
                document,
                WorkflowAction.REVIEW_APPROVE,
                leader,
                "Dong y thong qua",
                null,
                null,
                "hash_sig_reviewer_2"
        );
    }

    @Test
    @DisplayName("UC12: Duyệt văn bản ở trạng thái LEADER_REVIEW thì action là LEADER_APPROVE")
    void approve_whenLeaderReview_shouldUseLeaderApprove() {
        document.setStatus(DocumentStatus.PENDING_APPROVAL);
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
    @DisplayName("UC12: Duyệt văn bản ở trạng thái PENDING_APPROVAL thì action là LEADER_APPROVE")
    void approve_whenPendingApproval_shouldUseLeaderApprove() {
        document.setStatus(DocumentStatus.PENDING_APPROVAL);
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .comment("Lanh dao dong y phe duyet")
                .build();

        DocumentDetailResponse response = documentWorkflowService.approve(5001L, request);

        assertNotNull(response);
        verify(workflowEngine).executeTransition(
                document,
                WorkflowAction.LEADER_APPROVE,
                leader,
                "Lanh dao dong y phe duyet",
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("UC12: Hiệu trưởng duyệt văn bản có PDF sẽ tự động ký số hiển thị với ảnh con dấu/chữ ký")
    void approve_whenPendingApprovalWithPdf_shouldAutoSignVisible() {
        document.setStatus(DocumentStatus.PENDING_APPROVAL);
        document.setPrimaryFilePath("drafts/doc_5001.pdf");
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        byte[] rawPdfBytes = "%PDF-1.5 test".getBytes();
        byte[] signedPdfBytes = "%PDF-1.5 leader signed".getBytes();
        when(fileStorageService.loadFileBytes("drafts/doc_5001.pdf")).thenReturn(rawPdfBytes);

        PdfSigner.SignResult mockSignResult = new PdfSigner.SignResult(
                "1002",
                "Hiệu trưởng",
                "123456",
                "hash_sig_leader_visible",
                new java.util.Date(),
                "sig_approve_1002",
                null,
                signedPdfBytes
        );
        when(pdfSigner.signVisibleBytes(any(byte[].class), anyString(), anyString(), any(), any(), anyString(), any()))
                .thenReturn(mockSignResult);
        when(fileStorageService.storeFile(any(byte[].class), eq("leader_signed_5001.pdf"), eq("drafts")))
                .thenReturn("drafts/leader_signed_5001.pdf");

        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .comment("Hiệu trưởng đồng ý duyệt")
                .signaturePage(1)
                .signatureX(380f)
                .signatureY(120f)
                .signatureWidth(150f)
                .signatureHeight(80f)
                .build();

        DocumentDetailResponse response = documentWorkflowService.approve(5001L, request);

        assertNotNull(response);
        assertEquals("drafts/leader_signed_5001.pdf", document.getPrimaryFilePath());
        verify(pdfSigner).signVisibleBytes(any(byte[].class), eq("1002"), anyString(), any(), any(), contains("Hiệu trưởng đồng ý duyệt"), any());
        verify(workflowEngine).executeTransition(
                document,
                WorkflowAction.LEADER_APPROVE,
                leader,
                "Hiệu trưởng đồng ý duyệt",
                null,
                null,
                "hash_sig_leader_visible"
        );
    }


    @Test
    @DisplayName("UC12: Duyệt văn bản ở trạng thái WAITING_APPROVAL thì action là LEADER_APPROVE")
    void approve_whenWaitingApproval_shouldUseLeaderApprove() {
        document.setStatus(DocumentStatus.PENDING_APPROVAL);
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .comment("Lanh dao dong y phe duyet truc tiep")
                .build();

        DocumentDetailResponse response = documentWorkflowService.approve(5001L, request);

        assertNotNull(response);
        verify(workflowEngine).executeTransition(
                document,
                WorkflowAction.LEADER_APPROVE,
                leader,
                "Lanh dao dong y phe duyet truc tiep",
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
    @DisplayName("UC13: Ký nháy văn bản và lưu mã băm SHA-256")
    void initialSign_success() {
        document.setPrimaryFilePath("drafts/sample.pdf");
        byte[] mockBytes = new byte[]{1, 2, 3};

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));
        when(fileStorageService.loadFileBytes("drafts/sample.pdf")).thenReturn(mockBytes);
        when(pdfSigner.signBytes(any(byte[].class), anyString(), anyString(), anyString()))
                .thenReturn(new vn.edu.epu.eoffice_be.security.pki.PdfSigner.SignResult(
                        "1002", "leader", "123456", "mock_sha256_hash", new java.util.Date(), "sig_user_1002", null, mockBytes
                ));
        when(fileStorageService.storeFile(any(byte[].class), anyString(), anyString())).thenReturn("drafts/stamped.pdf");

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
        when(pdfSigner.signAndCertifyBytes(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new vn.edu.epu.eoffice_be.security.pki.PdfSigner.SignResult(
                        "1002", "leader", "123456", "official_sha256_hash", new java.util.Date(), "sig_publish_1002", null, mockBytes
                ));
        when(fileStorageService.storeFile(any(byte[].class), anyString(), anyString())).thenReturn("official/doc_5001.pdf");
        when(fileStorageService.computeSha256(any(byte[].class))).thenReturn("official_sha256_hash");
        when(documentBookService.assignNumber(any(AssignDocumentNumberRequest.class))).thenReturn(
                AssignDocumentNumberResponse.builder()
                        .documentNumber("125/QD-DHEPU")
                        .bookId(1001L)
                        .currentNumber(1)
                        .build()
        );

        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .bookId(1001L)
                .comment("Ky ban hanh chinh thuc")
                .build();

        DocumentDetailResponse response = documentWorkflowService.signAndPublish(5001L, request);

        assertNotNull(response);
        assertEquals("125/QD-DHEPU", document.getOfficialNumber());
        assertEquals("official/doc_5001.pdf", document.getOfficialFilePath());
        assertEquals("official_sha256_hash", document.getOfficialHash());
        assertEquals("/documents/5001/files/primary", response.getPrimaryFileUrl());
        assertEquals("/documents/5001/files/official", response.getOfficialFileUrl());

        verify(documentBookService).assignNumber(any(AssignDocumentNumberRequest.class));
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
    @DisplayName("UC14: Cán bộ Văn thư ban hành văn bản đã được cấp số, tự động ký nháy và niêm phong DocMDP P=1")
    void signAndPublish_byClerk_whenDocumentAlreadyHasOfficialNumber_shouldInitialSignAndCertify() {
        UserLogin clerk = UserLogin.builder()
                .id(3003L)
                .username("vanthu")
                .role(Role.CLERK)
                .build();

        document.setStatus(DocumentStatus.APPROVED);
        document.setOfficialNumber("1/QĐ-ĐHEPU");
        document.setPrimaryFilePath("drafts/leader_signed_5001.pdf");
        byte[] mockBytes = new byte[]{1, 2, 3};

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(3003L)).thenReturn(Optional.of(clerk));
        when(fileStorageService.loadFileBytes("drafts/leader_signed_5001.pdf")).thenReturn(mockBytes);

        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(new vn.edu.epu.eoffice_be.security.JwtUserPrincipal(3003L, "vanthu", "CLERK", true, List.of()));
        SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            when(pdfSigner.signBytes(any(byte[].class), eq("3003"), anyString(), contains("Văn thư ký nháy")))
                    .thenReturn(new vn.edu.epu.eoffice_be.security.pki.PdfSigner.SignResult(
                            "3003", "Cán bộ văn thư", "clerk123", "hash_clerk_initial", new java.util.Date(), "sig_user_3003", null, mockBytes
                    ));
            when(pdfSigner.signAndCertifyBytes(any(), eq("3003"), anyString(), any(), any(), anyString(), any()))
                    .thenReturn(new vn.edu.epu.eoffice_be.security.pki.PdfSigner.SignResult(
                            "3003", "Cán bộ văn thư", "clerk123", "official_sha256_hash", new java.util.Date(), "sig_publish_3003", null, mockBytes
                    ));
            when(fileStorageService.storeFile(any(byte[].class), eq("official_5001.pdf"), eq("official"))).thenReturn("official/official_5001.pdf");
            when(fileStorageService.computeSha256(any(byte[].class))).thenReturn("official_sha256_hash");

            DocumentPublishRequest request = DocumentPublishRequest.builder()
                    .comment("Văn thư ban hành chính thức")
                    .build();

            DocumentDetailResponse response = documentWorkflowService.signAndPublish(5001L, request);

            assertNotNull(response);
            assertEquals("1/QĐ-ĐHEPU", document.getOfficialNumber());
            assertEquals("official/official_5001.pdf", document.getOfficialFilePath());
            assertEquals("official_sha256_hash", document.getOfficialHash());

            // Không gọi assignNumber nữa vì đã có số hiệu
            verify(documentBookService, never()).assignNumber(any());
            // Văn thư đã ký nháy trước khi niêm phong
            verify(pdfSigner).signBytes(any(byte[].class), eq("3003"), anyString(), contains("Văn thư ký nháy"));
            // Niêm phong chính thức DocMDP P=1
            verify(pdfSigner).signAndCertifyBytes(any(), eq("3003"), anyString(), any(), any(), anyString(), any());
            verify(workflowEngine).executeTransition(
                    document,
                    WorkflowAction.SIGN_AND_PUBLISH,
                    clerk,
                    "Văn thư ban hành chính thức",
                    null,
                    null,
                    "official_sha256_hash"
            );
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("initialSign: nếu workflow transition thất bại thì phải xóa file đã lưu trong storage")
    void initialSign_transitionFails_shouldCleanUpStoredFile() {
        document.setPrimaryFilePath("drafts/sample.pdf");
        byte[] mockBytes = new byte[]{1, 2, 3};

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));
        when(fileStorageService.loadFileBytes("drafts/sample.pdf")).thenReturn(mockBytes);
        when(pdfSigner.signBytes(any(byte[].class), anyString(), anyString(), anyString()))
                .thenReturn(new vn.edu.epu.eoffice_be.security.pki.PdfSigner.SignResult(
                        "1002", "leader", "123456", "mock_sha256_hash", new java.util.Date(), "sig_user_1002", null, mockBytes
                ));
        when(fileStorageService.storeFile(any(byte[].class), anyString(), anyString())).thenReturn("drafts/stamped_failed.pdf");

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
        when(pdfSigner.signAndCertifyBytes(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new vn.edu.epu.eoffice_be.security.pki.PdfSigner.SignResult(
                        "1002", "leader", "123456", "official_sha256_hash", new java.util.Date(), "sig_publish_1002", null, mockBytes
                ));
        when(fileStorageService.storeFile(any(byte[].class), anyString(), anyString())).thenReturn("official/doc_failed.pdf");
        when(fileStorageService.computeSha256(any(byte[].class))).thenReturn("official_sha256_hash");

        when(documentBookService.assignNumber(any(AssignDocumentNumberRequest.class))).thenReturn(
                AssignDocumentNumberResponse.builder()
                        .documentNumber("125/QD-DHEPU")
                        .bookId(1001L)
                        .currentNumber(1)
                        .build()
        );

        doThrow(new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE))
                .when(workflowEngine).executeTransition(
                        any(), any(), any(), any(), any(), any(), any()
                );

        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .bookId(1001L)
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
    @DisplayName("UC13: Ký nháy thành công khi primaryFilePath null nhưng có tệp đính kèm PDF hợp lệ")
    void initialSign_whenPrimaryFilePathNull_butAttachmentExists_shouldSucceed() {
        document.setPrimaryFilePath(null);
        byte[] mockBytes = new byte[]{1, 2, 3};

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        DocumentAttachment attachment = DocumentAttachment.builder()
                .id(901L)
                .document(document)
                .originalFileName("ke_hoach.pdf")
                .storagePath("telegram")
                .storageFileName("file_12345")
                .fileExtension("PDF")
                .isMainDocument(true)
                .build();

        when(documentAttachmentRepository.findByDocumentIdAndDeleteFlgFalse(5001L))
                .thenReturn(List.of(attachment));

        org.springframework.core.io.ByteArrayResource res = new org.springframework.core.io.ByteArrayResource(mockBytes);
        when(unifiedStorageService.loadAsResource("telegram", "file_12345")).thenReturn(res);
        when(pdfSigner.signBytes(any(byte[].class), anyString(), anyString(), anyString()))
                .thenReturn(new vn.edu.epu.eoffice_be.security.pki.PdfSigner.SignResult(
                        "1002", "leader", "123456", "mock_sha256_hash", new java.util.Date(), "sig_user_1002", null, mockBytes
                ));
        when(fileStorageService.storeFile(any(byte[].class), anyString(), anyString())).thenReturn("drafts/stamped.pdf");

        DocumentInitialSignRequest request = DocumentInitialSignRequest.builder()
                .comment("Ký nháy từ file đính kèm")
                .build();

        DocumentDetailResponse response = documentWorkflowService.initialSign(5001L, request);

        assertNotNull(response);
        assertEquals("drafts/stamped.pdf", document.getPrimaryFilePath());
        verify(workflowEngine).executeTransition(
                document,
                WorkflowAction.INITIAL_SIGN,
                leader,
                "Ký nháy từ file đính kèm",
                null,
                null,
                "mock_sha256_hash"
        );
    }

    @Test
    @DisplayName("UC13: Báo lỗi BAD_REQUEST khi có tệp đính kèm nhưng không phải PDF")
    void initialSign_whenAttachmentNotPdf_shouldThrowBadRequest() {
        document.setPrimaryFilePath(null);

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        DocumentAttachment docxAttachment = DocumentAttachment.builder()
                .id(902L)
                .document(document)
                .originalFileName("ke_hoach.docx")
                .storagePath("telegram")
                .storageFileName("file_docx")
                .fileExtension("DOCX")
                .isMainDocument(true)
                .build();

        when(documentAttachmentRepository.findByDocumentIdAndDeleteFlgFalse(5001L))
                .thenReturn(List.of(docxAttachment));

        DocumentInitialSignRequest request = DocumentInitialSignRequest.builder()
                .comment("Ký nháy lỗi")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.initialSign(5001L, request));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("PDF"));
    }

    @Test
    @DisplayName("UC13: Báo lỗi BAD_REQUEST khi tệp đính kèm chính là DOCX kể cả khi có tệp đính kèm phụ là PDF")
    void initialSign_whenMainAttachmentIsNotPdf_evenIfOtherAttachmentIsPdf_shouldThrowBadRequest() {
        document.setPrimaryFilePath(null);

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        DocumentAttachment docxAttachment = DocumentAttachment.builder()
                .id(902L)
                .document(document)
                .originalFileName("ke_hoach.docx")
                .storagePath("telegram")
                .storageFileName("file_docx")
                .fileExtension("DOCX")
                .isMainDocument(true)
                .build();

        DocumentAttachment pdfAttachment = DocumentAttachment.builder()
                .id(903L)
                .document(document)
                .originalFileName("ke_hoach_scan.pdf")
                .storagePath("telegram")
                .storageFileName("file_pdf")
                .fileExtension("PDF")
                .isMainDocument(false)
                .build();

        when(documentAttachmentRepository.findByDocumentIdAndDeleteFlgFalse(5001L))
                .thenReturn(List.of(docxAttachment, pdfAttachment));

        DocumentInitialSignRequest request = DocumentInitialSignRequest.builder()
                .comment("Ký nháy lỗi")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.initialSign(5001L, request));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Tệp đính kèm chính"));
    }

    @Test
    @DisplayName("UC13: Khi không có tệp đính kèm nào đánh dấu chính, chọn tệp PDF đầu tiên để ký thành công")
    void initialSign_whenNoMainAttachment_butHasPdfAttachment_shouldSucceed() {
        document.setPrimaryFilePath(null);
        byte[] mockBytes = new byte[]{1, 2, 3};

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        DocumentAttachment docxAttachment = DocumentAttachment.builder()
                .id(902L)
                .document(document)
                .originalFileName("ke_hoach.docx")
                .storagePath("telegram")
                .storageFileName("file_docx")
                .fileExtension("DOCX")
                .isMainDocument(false)
                .build();

        DocumentAttachment pdfAttachment = DocumentAttachment.builder()
                .id(903L)
                .document(document)
                .originalFileName("ke_hoach_scan.pdf")
                .storagePath("telegram")
                .storageFileName("file_pdf")
                .fileExtension("PDF")
                .isMainDocument(false)
                .build();

        when(documentAttachmentRepository.findByDocumentIdAndDeleteFlgFalse(5001L))
                .thenReturn(List.of(docxAttachment, pdfAttachment));

        org.springframework.core.io.ByteArrayResource res = new org.springframework.core.io.ByteArrayResource(mockBytes);
        when(unifiedStorageService.loadAsResource("telegram", "file_pdf")).thenReturn(res);
        when(pdfSigner.signBytes(any(byte[].class), anyString(), anyString(), anyString()))
                .thenReturn(new vn.edu.epu.eoffice_be.security.pki.PdfSigner.SignResult(
                        "1002", "leader", "123456", "mock_sha256_hash", new java.util.Date(), "sig_user_1002", null, mockBytes
                ));
        when(fileStorageService.storeFile(any(byte[].class), anyString(), anyString())).thenReturn("drafts/stamped.pdf");

        DocumentInitialSignRequest request = DocumentInitialSignRequest.builder()
                .comment("Ký nháy tệp PDF khi không có tệp chính")
                .build();

        DocumentDetailResponse response = documentWorkflowService.initialSign(5001L, request);

        assertNotNull(response);
        assertEquals("drafts/stamped.pdf", document.getPrimaryFilePath());
        verify(workflowEngine).executeTransition(
                document,
                WorkflowAction.INITIAL_SIGN,
                leader,
                "Ký nháy tệp PDF khi không có tệp chính",
                null,
                null,
                "mock_sha256_hash"
        );
    }

    @Test
    @DisplayName("UC13: Có tệp đính kèm PDF nhưng đọc từ storage thất bại thì ném lỗi STORAGE_ERROR")
    void initialSign_whenPdfAttachmentExists_butStorageFails_shouldThrowStorageError() {
        document.setPrimaryFilePath(null);

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        DocumentAttachment attachment = DocumentAttachment.builder()
                .id(901L)
                .document(document)
                .originalFileName("ke_hoach.pdf")
                .storagePath("telegram")
                .storageFileName("file_12345")
                .fileExtension("PDF")
                .isMainDocument(true)
                .build();

        when(documentAttachmentRepository.findByDocumentIdAndDeleteFlgFalse(5001L))
                .thenReturn(List.of(attachment));

        when(unifiedStorageService.loadAsResource("telegram", "file_12345"))
                .thenThrow(new RuntimeException("Telegram storage connection error"));

        DocumentInitialSignRequest request = DocumentInitialSignRequest.builder()
                .comment("Ký nháy lỗi storage")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.initialSign(5001L, request));
        assertEquals(ErrorCode.STORAGE_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("UC14: Ký phát hành thất bại khi văn bản chưa có file đính kèm chính")
    void signAndPublish_whenPrimaryFileMissing_shouldThrowException() {
        document.setPrimaryFilePath(null);

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .bookId(1001L)
                .comment("Ky ban hanh")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.signAndPublish(5001L, request));
        assertEquals(ErrorCode.DOCUMENT_PRIMARY_FILE_MISSING, ex.getErrorCode());
        verifyNoInteractions(documentBookService);
    }

    @Test
    @DisplayName("UC14: Ký phát hành thất bại khi văn bản có tệp đính kèm nhưng không có tệp nào là PDF")
    void signAndPublish_whenAttachmentIsNotPdf_shouldThrowBadRequestAndNotAssignNumber() {
        document.setPrimaryFilePath(null);

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        DocumentAttachment docxAttachment = DocumentAttachment.builder()
                .id(905L)
                .document(document)
                .originalFileName("cong_van.docx")
                .storagePath("telegram")
                .storageFileName("file_docx")
                .fileExtension("DOCX")
                .isMainDocument(false)
                .build();

        when(documentAttachmentRepository.findByDocumentIdAndDeleteFlgFalse(5001L))
                .thenReturn(List.of(docxAttachment));

        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .bookId(1001L)
                .comment("Ky ban hanh")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.signAndPublish(5001L, request));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("PDF"));
        verifyNoInteractions(documentBookService);
    }

    @Test
    @DisplayName("UC14: Ký phát hành thất bại khi tệp đính kèm chính không phải PDF kể cả khi có tệp phụ là PDF")
    void signAndPublish_whenMainAttachmentIsNotPdf_shouldThrowBadRequestAndNotAssignNumber() {
        document.setPrimaryFilePath(null);

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        DocumentAttachment docxAttachment = DocumentAttachment.builder()
                .id(906L)
                .document(document)
                .originalFileName("cong_van.docx")
                .storagePath("telegram")
                .storageFileName("file_docx")
                .fileExtension("DOCX")
                .isMainDocument(true)
                .build();

        DocumentAttachment pdfAttachment = DocumentAttachment.builder()
                .id(907L)
                .document(document)
                .originalFileName("phu_luc.pdf")
                .storagePath("telegram")
                .storageFileName("file_pdf")
                .fileExtension("PDF")
                .isMainDocument(false)
                .build();

        when(documentAttachmentRepository.findByDocumentIdAndDeleteFlgFalse(5001L))
                .thenReturn(List.of(docxAttachment, pdfAttachment));

        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .bookId(1001L)
                .comment("Ky ban hanh")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.signAndPublish(5001L, request));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Tệp đính kèm chính"));
        verifyNoInteractions(documentBookService);
    }

    @Test
    @DisplayName("UC14: Ký phát hành thất bại khi thiếu bookId và officialNumber")
    void signAndPublish_whenBookIdMissing_shouldThrowException() {
        document.setPrimaryFilePath("drafts/sample.pdf");
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .comment("Ky ban hanh")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.signAndPublish(5001L, request));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
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

        verify(pdfSigner, never()).signBytes(any(), any(), any(), any());
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

        verify(pdfSigner, never()).signAndCertifyBytes(any(), any(), any(), any(), any(), any(), any());
        verify(fileStorageService, never()).storeFile(any(), any(), any());
    }

    @Test
    @DisplayName("signAndPublish: ném VALIDATION_ERROR khi effectiveDate trước issueDate")
    void signAndPublish_whenEffectiveDateBeforeIssueDate_shouldThrowValidationError() {
        document.setPrimaryFilePath("drafts/sample.pdf");
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        Instant now = Instant.now();
        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .officialNumber("125/QD-DHEPU")
                .issueDate(now)
                .effectiveDate(now.minus(2, java.time.temporal.ChronoUnit.DAYS))
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.signAndPublish(5001L, request));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("effectiveDate"));

        verify(pdfSigner, never()).signAndCertifyBytes(any(), any(), any(), any(), any(), any(), any());
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
        @Test
    @DisplayName("signAndPublish: thành công khi effectiveDate bằng đúng issueDate (effectiveDate == issueDate)")
    void signAndPublish_whenEffectiveDateEqualsIssueDate_shouldSucceed() {
        document.setPrimaryFilePath("drafts/sample.pdf");
        byte[] mockBytes = new byte[]{1, 2, 3};
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));
        when(fileStorageService.loadFileBytes("drafts/sample.pdf")).thenReturn(mockBytes);
        when(pdfSigner.signAndCertifyBytes(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new vn.edu.epu.eoffice_be.security.pki.PdfSigner.SignResult(
                        "1002", "leader", "123456", "hash", new java.util.Date(), "sig_publish_1002", null, mockBytes
                ));
        when(fileStorageService.storeFile(any(), anyString(), anyString())).thenReturn("official/doc_5001.pdf");
        when(fileStorageService.computeSha256(any(byte[].class))).thenReturn("hash");

        Instant now = Instant.now();
        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .officialNumber("125/QD-DHEPU")
                .issueDate(now)
                .effectiveDate(now) // Bằng nhau
                .build();

        DocumentDetailResponse response = documentWorkflowService.signAndPublish(5001L, request);
        assertNotNull(response);
        assertEquals(now, document.getEffectiveDate());
        assertEquals(now, document.getIssueDate());
    }

    @Test
    @DisplayName("UC14: Ký phát hành tích hợp cả ký nháy của Hiệu trưởng và ký tọa độ niêm phong DocMDP")
    void signAndPublish_withCoordinates_shouldInitialSignAndCertifyAtPosition() {
        document.setPrimaryFilePath("drafts/sample.pdf");
        byte[] rawBytes = new byte[]{1, 2, 3};
        byte[] initialSignedBytes = new byte[]{4, 5, 6};
        byte[] certifiedBytes = new byte[]{7, 8, 9};

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));
        when(fileStorageService.loadFileBytes("drafts/sample.pdf")).thenReturn(rawBytes);

        when(pdfSigner.signBytes(any(byte[].class), anyString(), anyString(), anyString()))
                .thenReturn(new vn.edu.epu.eoffice_be.security.pki.PdfSigner.SignResult(
                        "1002", "leader", "123456", "initial_hash", new java.util.Date(), "sig_user_1002", null, initialSignedBytes
                ));

        when(pdfSigner.signAndCertifyBytes(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new vn.edu.epu.eoffice_be.security.pki.PdfSigner.SignResult(
                        "1002", "leader", "123456", "official_hash", new java.util.Date(), "sig_publish_1002", null, certifiedBytes
                ));
        when(fileStorageService.storeFile(any(), anyString(), anyString())).thenReturn("official/doc_5001.pdf");
        when(fileStorageService.computeSha256(any(byte[].class))).thenReturn("official_hash");

        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .officialNumber("125/QD-DHEPU")
                .signaturePage(2)
                .signatureX(400f)
                .signatureY(200f)
                .signatureWidth(180f)
                .signatureHeight(60f)
                .build();

        DocumentDetailResponse response = documentWorkflowService.signAndPublish(5001L, request);

        assertNotNull(response);
        assertEquals("official/doc_5001.pdf", document.getOfficialFilePath());
        assertEquals("official_hash", document.getOfficialHash());

        verify(pdfSigner).signBytes(eq(rawBytes), eq("1002"), anyString(), anyString());
        verify(pdfSigner).signAndCertifyBytes(
                eq(initialSignedBytes),
                eq("1002"),
                anyString(),
                any(),
                any(),
                anyString(),
                org.mockito.ArgumentMatchers.argThat(p -> p != null && p.page() == 2 && p.x() == 400f && p.y() == 200f)
        );
    }

    @Test
    @DisplayName("signAndPublish: fallback thành công khi effectiveDate null (effectiveDate = issueDate)")
    void signAndPublish_whenEffectiveDateNull_shouldFallbackToIssueDate() {
        document.setPrimaryFilePath("drafts/sample.pdf");
        byte[] mockBytes = new byte[]{1, 2, 3};
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));
        when(fileStorageService.loadFileBytes("drafts/sample.pdf")).thenReturn(mockBytes);
        when(pdfSigner.signAndCertifyBytes(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new vn.edu.epu.eoffice_be.security.pki.PdfSigner.SignResult(
                        "1002", "leader", "123456", "hash", new java.util.Date(), "sig_publish_1002", null, mockBytes
                ));
        when(fileStorageService.storeFile(any(), anyString(), anyString())).thenReturn("official/doc_5001.pdf");
        when(fileStorageService.computeSha256(any(byte[].class))).thenReturn("hash");

        Instant issueDate = Instant.now().plus(1, ChronoUnit.DAYS);
        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .officialNumber("125/QD-DHEPU")
                .issueDate(issueDate)
                .effectiveDate(null) // null fallback
                .build();

        DocumentDetailResponse response = documentWorkflowService.signAndPublish(5001L, request);
        assertNotNull(response);
        assertEquals(issueDate, document.getEffectiveDate());
    }

    @Test
    @DisplayName("signAndPublish: ném VALIDATION_ERROR khi issueDate null nhưng effectiveDate ở quá khứ")
    void signAndPublish_whenIssueDateNullAndEffectiveDateInPast_shouldThrowValidationError() {
        document.setPrimaryFilePath("drafts/sample.pdf");
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .officialNumber("125/QD-DHEPU")
                .issueDate(null) // issueDate tự lấy Instant.now()
                .effectiveDate(Instant.now().minus(5, ChronoUnit.DAYS)) // Quá khứ -> vi phạm
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.signAndPublish(5001L, request));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("signAndPublish: khi request không truyền officialNumber thì tự kế thừa từ số đã cấp ở sổ văn bản")
    void signAndPublish_whenRequestOfficialNumberBlank_shouldInheritFromDocument() {
        document.setPrimaryFilePath("drafts/sample.pdf");
        document.setOfficialNumber("99/QD-DHEPU"); // Đã cấp trước đó từ sổ
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        byte[] mockBytes = new byte[]{1, 2, 3};
        when(fileStorageService.loadFileBytes("drafts/sample.pdf")).thenReturn(mockBytes);
        when(pdfSigner.signAndCertifyBytes(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new vn.edu.epu.eoffice_be.security.pki.PdfSigner.SignResult(
                        "1002", "leader", "123456", "hash_sha256", new java.util.Date(), "sig_publish_1002", null, mockBytes
                ));
        when(fileStorageService.storeFile(any(byte[].class), anyString(), anyString())).thenReturn("official/doc_5001.pdf");
        when(fileStorageService.computeSha256(any(byte[].class))).thenReturn("hash_sha256");

        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .officialNumber(null) // Để trống số hiệu
                .comment("Ký phát hành kế thừa số sổ")
                .build();

        DocumentDetailResponse response = documentWorkflowService.signAndPublish(5001L, request);

        assertNotNull(response);
        assertEquals("99/QD-DHEPU", document.getOfficialNumber());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("signAndPublish: khi số hiệu trùng với văn bản khác đã có thì ném DOCUMENT_NUMBER_ALREADY_EXISTS")
    void signAndPublish_whenDuplicateOfficialNumber_shouldThrowConflict() {
        document.setPrimaryFilePath("drafts/sample.pdf");
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));
        when(documentRepository.existsByOfficialNumberAndIdNotAndDeleteFlgFalse("125/QD-DHEPU", 5001L)).thenReturn(true);

        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .officialNumber("125/QD-DHEPU")
                .comment("Ký ban hành nhưng trùng số")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentWorkflowService.signAndPublish(5001L, request));
        assertEquals(ErrorCode.DOCUMENT_NUMBER_ALREADY_EXISTS, ex.getErrorCode());
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
        when(fileStorageService.loadFileBytes("drafts/test_signed.pdf")).thenReturn(new byte[]{1, 2, 3});

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

    @Test
    @DisplayName("REVOKE: Thu hồi văn bản đã ban hành thành công và đồng bộ CRL")
    void revokeDocument_success() {
        Long docId = 5001L;
        document.setStatus(DocumentStatus.PUBLISHED);
        document.setPrimaryFilePath("official/test_signed.pdf");
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(document));
        when(fileStorageService.loadFileBytes("official/test_signed.pdf")).thenReturn(new byte[]{1, 2, 3});

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
}
