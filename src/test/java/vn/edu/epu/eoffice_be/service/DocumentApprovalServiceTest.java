package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import vn.edu.epu.eoffice_be.common.enums.ApprovalRequestStatus;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.RecallApprovalRequest;
import vn.edu.epu.eoffice_be.dto.request.SubmitApprovalRequest;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentApprovalRequest;
import vn.edu.epu.eoffice_be.entity.DocumentProcessLog;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.PositionAssignment;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.event.DocumentSubmittedEvent;
import vn.edu.epu.eoffice_be.repository.DocumentApprovalRequestRepository;
import vn.edu.epu.eoffice_be.repository.DocumentAttachmentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentProcessLogRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.PositionAssignmentRepository;
import vn.edu.epu.eoffice_be.repository.PositionPermissionRepository;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.security.pki.PdfSigner;
import vn.edu.epu.eoffice_be.service.FileStorageService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentApprovalServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentAttachmentRepository documentAttachmentRepository;
    @Mock
    private DocumentApprovalRequestRepository documentApprovalRequestRepository;
    @Mock
    private DocumentProcessLogRepository documentProcessLogRepository;
    @Mock
    private UserLoginRepository userLoginRepository;
    @Mock
    private UserInfoRepository userInfoRepository;
    @Mock
    private PositionAssignmentRepository positionAssignmentRepository;
    @Mock
    private PositionPermissionRepository positionPermissionRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private DocumentDraftService documentDraftService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private vn.edu.epu.eoffice_be.storage.FileStorageService unifiedStorageService;
    @Mock
    private PdfSigner pdfSigner;

    @InjectMocks
    private DocumentApprovalService documentApprovalService;

    @Test
    @DisplayName("Gửi văn bản để duyệt thành công (UC11 - Luồng 2.1)")
    void submitForApproval_Success() {
        Long docId = 1L;
        Long senderId = 100L;
        Long approverId = 200L;

        UserLogin sender = UserLogin.builder().id(senderId).username("author").role(Role.USER).build();
        UserLogin approver = UserLogin.builder().id(approverId).username("dean").role(Role.ADMIN).build();

        Document doc = Document.builder()
                .id(docId)
                .draftCode("DT-2026-00001")
                .title("Kế hoạch công tác năm 2026")
                .summary("Chi tiết kế hoạch")
                .content("Nội dung kế hoạch")
                .creatorUser(sender)
                .status(DocumentStatus.DRAFT)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(senderId)).thenReturn(Optional.of(sender));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentAttachmentRepository.existsByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(docId)).thenReturn(true);
        when(userLoginRepository.findByIdAndDeleteFlgFalse(approverId)).thenReturn(Optional.of(approver));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentDraftService.mapToDocumentResponse(any())).thenReturn(DocumentResponse.builder().id(docId).status(DocumentStatus.IN_REVIEW).build());

        SubmitApprovalRequest request = SubmitApprovalRequest.builder()
                .approverUserId(approverId)
                .submitNote("Kính gửi thầy duyệt giúp")
                .build();

        DocumentResponse response = documentApprovalService.submitForApproval(docId, request, senderId);

        assertNotNull(response);
        assertEquals(DocumentStatus.IN_REVIEW, doc.getStatus());
        assertNotNull(doc.getSubmittedAt());

        verify(documentApprovalRequestRepository).save(any(DocumentApprovalRequest.class));
        verify(documentProcessLogRepository).save(any(DocumentProcessLog.class));
        verify(eventPublisher).publishEvent(any(DocumentSubmittedEvent.class));
    }

    @Test
    @DisplayName("Gửi duyệt tự động ký nháy khi hồ sơ có tệp PDF (Task 1)")
    void submitForApproval_WithPdfAttachment_AutoInitialSignsSuccessfully() {
        Long docId = 1L;
        Long senderId = 100L;
        Long approverId = 200L;

        UserLogin sender = UserLogin.builder().id(senderId).username("author").role(Role.USER).build();
        UserLogin approver = UserLogin.builder().id(approverId).username("dean").role(Role.ADMIN).build();

        Document doc = Document.builder()
                .id(docId)
                .draftCode("DT-2026-00001")
                .title("Kế hoạch công tác năm 2026")
                .summary("Chi tiết kế hoạch")
                .content("Nội dung kế hoạch")
                .creatorUser(sender)
                .status(DocumentStatus.DRAFT)
                .primaryFilePath("drafts/doc_1.pdf")
                .build();

        byte[] rawPdfBytes = "%PDF-1.5 test".getBytes();
        byte[] signedPdfBytes = "%PDF-1.5 signed".getBytes();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(senderId)).thenReturn(Optional.of(sender));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentAttachmentRepository.existsByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(docId)).thenReturn(true);
        when(userLoginRepository.findByIdAndDeleteFlgFalse(approverId)).thenReturn(Optional.of(approver));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentDraftService.mapToDocumentResponse(any())).thenReturn(DocumentResponse.builder().id(docId).status(DocumentStatus.IN_REVIEW).build());

        when(fileStorageService.loadFileBytes("drafts/doc_1.pdf")).thenReturn(rawPdfBytes);
        PdfSigner.SignResult mockSignResult = new PdfSigner.SignResult(
                String.valueOf(senderId),
                "author",
                "123456",
                "sha256hash123",
                new java.util.Date(),
                "sig_user_" + senderId,
                null,
                signedPdfBytes
        );
        when(pdfSigner.signBytes(any(), any(), any(), any(), any(), any())).thenReturn(mockSignResult);
        when(fileStorageService.storeFile(any(), any(), any())).thenReturn("drafts/initial_signed_1.pdf");

        SubmitApprovalRequest request = SubmitApprovalRequest.builder()
                .approverUserId(approverId)
                .submitNote("Gửi thầy duyệt")
                .build();

        DocumentResponse response = documentApprovalService.submitForApproval(docId, request, senderId);

        assertNotNull(response);
        assertEquals(DocumentStatus.IN_REVIEW, doc.getStatus());
        assertEquals("drafts/initial_signed_1.pdf", doc.getPrimaryFilePath());

        verify(pdfSigner).signBytes(any(), any(), any(), any(), any(), any());
        verify(fileStorageService).storeFile(any(), any(), any());
    }

    @Test
    @DisplayName("Gửi duyệt thất bại khi người duyệt không có thẩm quyền (APPROVER_NOT_ELIGIBLE)")
    void submitForApproval_IneligibleApprover_ThrowsApproverNotEligible() {
        Long docId = 1L;
        Long senderId = 100L;
        Long approverId = 200L;

        UserLogin sender = UserLogin.builder().id(senderId).username("author").role(Role.USER).build();
        UserLogin approver = UserLogin.builder().id(approverId).username("staff").role(Role.USER).build();

        Document doc = Document.builder()
                .id(docId)
                .title("Tiêu đề")
                .summary("Trích yếu")
                .content("Nội dung")
                .creatorUser(sender)
                .status(DocumentStatus.DRAFT)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(senderId)).thenReturn(Optional.of(sender));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentAttachmentRepository.existsByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(docId)).thenReturn(true);
        when(userLoginRepository.findByIdAndDeleteFlgFalse(approverId)).thenReturn(Optional.of(approver));

        SubmitApprovalRequest request = SubmitApprovalRequest.builder()
                .approverUserId(approverId)
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentApprovalService.submitForApproval(docId, request, senderId));
        assertEquals(ErrorCode.APPROVER_NOT_ELIGIBLE, ex.getErrorCode());

        verify(eventPublisher, never()).publishEvent(any());
        verify(documentRepository, never()).save(any());
        assertEquals(DocumentStatus.DRAFT, doc.getStatus());
    }

    @Test
    @DisplayName("Gửi duyệt thất bại khi hồ sơ chưa đầy đủ (không có tệp và không có nội dung)")
    void submitForApproval_IncompleteDossier_ShouldThrowIncompleteDossier() {
        Long docId = 1L;
        Long senderId = 100L;
        Long approverId = 200L;

        UserLogin sender = UserLogin.builder().id(senderId).username("author").role(Role.USER).build();
        Document doc = Document.builder()
                .id(docId)
                .title("Tiêu đề")
                .summary("Trích yếu")
                .content("") // Không có nội dung
                .creatorUser(sender)
                .status(DocumentStatus.DRAFT)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(senderId)).thenReturn(Optional.of(sender));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentAttachmentRepository.existsByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(docId)).thenReturn(false);
        when(documentAttachmentRepository.existsByDocumentIdAndDeleteFlgFalse(docId)).thenReturn(false);

        SubmitApprovalRequest request = SubmitApprovalRequest.builder().approverUserId(approverId).build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentApprovalService.submitForApproval(docId, request, senderId));
        assertEquals(ErrorCode.DOCUMENT_INCOMPLETE_DOSSIER, ex.getErrorCode());
    }

    @Test
    @DisplayName("Thu hồi yêu cầu duyệt thành công khi người duyệt chưa xử lý (UC11 - Luồng 2.2)")
    void recallApproval_Success() {
        Long docId = 1L;
        Long userId = 100L;

        UserLogin user = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        Document doc = Document.builder()
                .id(docId)
                .draftCode("DT-2026-00001")
                .creatorUser(user)
                .status(DocumentStatus.IN_REVIEW)
                .build();

        DocumentApprovalRequest approvalReq = DocumentApprovalRequest.builder()
                .id(50L)
                .document(doc)
                .status(ApprovalRequestStatus.PENDING)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(user));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentApprovalRequestRepository.findTopByDocumentIdAndDeleteFlgFalseOrderBySubmittedAtDesc(docId))
                .thenReturn(Optional.of(approvalReq));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentDraftService.mapToDocumentResponse(any())).thenReturn(DocumentResponse.builder().id(docId).status(DocumentStatus.DRAFT).build());

        RecallApprovalRequest request = RecallApprovalRequest.builder().recallReason("Bổ sung số liệu").build();
        DocumentResponse response = documentApprovalService.recallApproval(docId, request, userId);

        assertNotNull(response);
        assertEquals(DocumentStatus.DRAFT, doc.getStatus());
        assertEquals(ApprovalRequestStatus.RECALLED, approvalReq.getStatus());

        verify(documentApprovalRequestRepository).save(approvalReq);
        verify(documentProcessLogRepository).save(any(DocumentProcessLog.class));
    }

    @Test
    @DisplayName("Thu hồi thất bại khi người duyệt đã xử lý (DOCUMENT_CANNOT_RECALL)")
    void recallApproval_AlreadyApproved_ShouldThrowCannotRecall() {
        Long docId = 1L;
        Long userId = 100L;

        UserLogin user = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        Document doc = Document.builder()
                .id(docId)
                .creatorUser(user)
                .status(DocumentStatus.IN_REVIEW)
                .build();

        DocumentApprovalRequest approvalReq = DocumentApprovalRequest.builder()
                .id(50L)
                .document(doc)
                .status(ApprovalRequestStatus.APPROVED) // Đã duyệt rồi
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(user));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentApprovalRequestRepository.findTopByDocumentIdAndDeleteFlgFalseOrderBySubmittedAtDesc(docId))
                .thenReturn(Optional.of(approvalReq));

        RecallApprovalRequest request = RecallApprovalRequest.builder().recallReason("Muốn thu hồi").build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentApprovalService.recallApproval(docId, request, userId));
        assertEquals(ErrorCode.DOCUMENT_CANNOT_RECALL, ex.getErrorCode());
    }

    @Test
    @DisplayName("Kiểm tra DocumentApprovalRequest no-args constructor khởi tạo status PENDING, submittedAt null trước khi persist")
    void documentApprovalRequest_NoArgsConstructor_InitializesDefaults() {
        DocumentApprovalRequest req = new DocumentApprovalRequest();
        assertNotNull(req.getStatus());
        assertEquals(ApprovalRequestStatus.PENDING, req.getStatus());
        org.junit.jupiter.api.Assertions.assertNull(req.getSubmittedAt());
        org.junit.jupiter.api.Assertions.assertFalse(req.isDeleteFlg());

        // Kiểm tra @PrePersist gán submittedAt khi persist
        req.prePersist();
        assertNotNull(req.getSubmittedAt());
        assertEquals(ApprovalRequestStatus.PENDING, req.getStatus());

        // Kiểm tra @PrePersist tự gán status = PENDING nếu status bị null trước khi persist
        DocumentApprovalRequest nullStatusReq = new DocumentApprovalRequest();
        nullStatusReq.setStatus(null);
        org.junit.jupiter.api.Assertions.assertNull(nullStatusReq.getStatus());
        nullStatusReq.prePersist();
        assertEquals(ApprovalRequestStatus.PENDING, nullStatusReq.getStatus());
    }

    @Test
    @DisplayName("Kiểm tra DocumentApprovalRequest builder: submittedAt null trước khi persist và được gán khi prePersist")
    void documentApprovalRequest_Builder_SubmittedAtNullBeforePersist() {
        DocumentApprovalRequest req = DocumentApprovalRequest.builder().build();
        org.junit.jupiter.api.Assertions.assertNull(req.getSubmittedAt());
        req.prePersist();
        assertNotNull(req.getSubmittedAt());
    }

    @Test
    @DisplayName("Kiểm tra tính hợp lệ của người duyệt (isUserEligibleApprover)")
    void isUserEligibleApprover_Scenarios() {
        // null hoặc deleted
        org.junit.jupiter.api.Assertions.assertFalse(documentApprovalService.isUserEligibleApprover(null));
        UserLogin deletedUser = UserLogin.builder().id(1L).deleteFlg(true).build();
        org.junit.jupiter.api.Assertions.assertFalse(documentApprovalService.isUserEligibleApprover(deletedUser));

        // ADMIN hoặc SYSTEM_ADMIN
        UserLogin admin = UserLogin.builder().id(2L).role(Role.ADMIN).build();
        org.junit.jupiter.api.Assertions.assertTrue(documentApprovalService.isUserEligibleApprover(admin));
        UserLogin sysAdmin = UserLogin.builder().id(3L).role(Role.SYSTEM_ADMIN).build();
        org.junit.jupiter.api.Assertions.assertTrue(documentApprovalService.isUserEligibleApprover(sysAdmin));

        // User thường có PositionAssignment hợp lệ
        UserLogin dean = UserLogin.builder().id(4L).role(Role.USER).build();
        vn.edu.epu.eoffice_be.entity.PositionAssignment activePa = vn.edu.epu.eoffice_be.entity.PositionAssignment.builder()
                .id(10L)
                .userLogin(dean)
                .positionCd("TK")
                .deleteFlg(false)
                .build();
        when(positionAssignmentRepository.findByUserLoginIdAndDeleteFlgFalse(4L)).thenReturn(java.util.List.of(activePa));
        org.junit.jupiter.api.Assertions.assertTrue(documentApprovalService.isUserEligibleApprover(dean));

        // User thường có PositionAssignment không phải lãnh đạo
        UserLogin staff = UserLogin.builder().id(5L).role(Role.USER).build();
        vn.edu.epu.eoffice_be.entity.PositionAssignment staffPa = vn.edu.epu.eoffice_be.entity.PositionAssignment.builder()
                .id(11L)
                .userLogin(staff)
                .positionCd("GV")
                .deleteFlg(false)
                .build();
        when(positionAssignmentRepository.findByUserLoginIdAndDeleteFlgFalse(5L)).thenReturn(java.util.List.of(staffPa));
        org.junit.jupiter.api.Assertions.assertFalse(documentApprovalService.isUserEligibleApprover(staff));
    }

    @Test
    @DisplayName("Lấy danh sách người duyệt đủ thẩm quyền (getEligibleApprovers)")
    void getEligibleApprovers_Success() {
        Long docId = 1L;
        Long userId = 100L;
        Document doc = Document.builder().id(docId).build();
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));

        UserLogin leader = UserLogin.builder().id(200L).username("leader").build();
        vn.edu.epu.eoffice_be.entity.PositionAssignment pa = vn.edu.epu.eoffice_be.entity.PositionAssignment.builder()
                .id(1L)
                .userLogin(leader)
                .positionCd("HT")
                .deleteFlg(false)
                .build();
        when(positionAssignmentRepository.findActiveAssignmentsByPositionCds(any(), any())).thenReturn(java.util.List.of(pa));
        when(userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(200L))
                .thenReturn(Optional.of(vn.edu.epu.eoffice_be.entity.UserInfo.builder().fullName("Nguyễn Văn Hiệu Trưởng").build()));

        var result = documentApprovalService.getEligibleApprovers(docId, userId);
        org.junit.jupiter.api.Assertions.assertFalse(result.isEmpty());
        assertEquals("leader", result.get(0).getUsername());
        assertEquals("Hiệu trưởng", result.get(0).getPositionName());
    }

    @Test
    @DisplayName("Lấy danh sách người duyệt fallback về admin khi không có cán bộ nào")
    void getEligibleApprovers_FallbackAdmin_Success() {
        Long docId = 1L;
        Long userId = 100L;
        Document doc = Document.builder().id(docId).build();
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));

        when(positionAssignmentRepository.findActiveAssignmentsByPositionCds(any(), any())).thenReturn(java.util.List.of());
        UserLogin admin = UserLogin.builder().id(999L).username("admin").role(Role.ADMIN).build();
        when(userLoginRepository.findByRoleInAndDeleteFlgFalse(any())).thenReturn(java.util.List.of(admin));

        var result = documentApprovalService.getEligibleApprovers(docId, userId);
        org.junit.jupiter.api.Assertions.assertFalse(result.isEmpty());
        assertEquals("admin", result.get(0).getUsername());
        assertEquals("Quản trị viên", result.get(0).getPositionName());
    }

    @Test
    @DisplayName("Lấy danh sách người duyệt loại bỏ phân bổ đã xóa mềm hoặc đã miễn nhiệm")
    void getEligibleApprovers_DismissedOrDeletedAssignment_Excluded() {
        Long docId = 1L;
        Long userId = 100L;
        Document doc = Document.builder().id(docId).build();
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));

        UserLogin dismissedUser = UserLogin.builder().id(201L).username("dismissedLeader").build();
        vn.edu.epu.eoffice_be.entity.PositionAssignment dismissedPa = vn.edu.epu.eoffice_be.entity.PositionAssignment.builder()
                .id(1L)
                .userLogin(dismissedUser)
                .positionCd("HT")
                .deleteFlg(false)
                .actualDismissalDate(java.time.Instant.now())
                .build();

        UserLogin deletedUser = UserLogin.builder().id(202L).username("deletedAssignmentLeader").build();
        vn.edu.epu.eoffice_be.entity.PositionAssignment deletedPa = vn.edu.epu.eoffice_be.entity.PositionAssignment.builder()
                .id(2L)
                .userLogin(deletedUser)
                .positionCd("PHT")
                .deleteFlg(true)
                .build();

        UserLogin activeUser = UserLogin.builder().id(203L).username("activeLeader").build();
        vn.edu.epu.eoffice_be.entity.PositionAssignment activePa = vn.edu.epu.eoffice_be.entity.PositionAssignment.builder()
                .id(3L)
                .userLogin(activeUser)
                .positionCd("TK")
                .deleteFlg(false)
                .build();

        when(positionAssignmentRepository.findActiveAssignmentsByPositionCds(any(), any()))
                .thenReturn(java.util.List.of(dismissedPa, deletedPa, activePa));
        when(userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(203L))
                .thenReturn(Optional.of(vn.edu.epu.eoffice_be.entity.UserInfo.builder().fullName("Trưởng Khoa Hoạt Động").build()));

        var result = documentApprovalService.getEligibleApprovers(docId, userId);
        assertEquals(1, result.size());
        assertEquals("activeLeader", result.get(0).getUsername());
        assertEquals("Trưởng khoa", result.get(0).getPositionName());
    }

    @Test
    @DisplayName("Gửi duyệt thất bại khi người gửi không phải người tạo và không phải admin (FORBIDDEN)")
    void submitForApproval_Forbidden_ThrowsForbidden() {
        Long docId = 1L;
        Long senderId = 200L;
        UserLogin sender = UserLogin.builder().id(senderId).role(Role.USER).build();
        UserLogin creator = UserLogin.builder().id(100L).role(Role.USER).build();
        Document doc = Document.builder().id(docId).creatorUser(creator).status(DocumentStatus.DRAFT).build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(senderId)).thenReturn(Optional.of(sender));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));

        SubmitApprovalRequest req = SubmitApprovalRequest.builder().approverUserId(300L).build();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentApprovalService.submitForApproval(docId, req, senderId));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Gửi duyệt thất bại khi trạng thái văn bản không hợp lệ (DOCUMENT_INVALID_STATUS)")
    void submitForApproval_InvalidStatus_ThrowsInvalidStatus() {
        Long docId = 1L;
        Long senderId = 100L;
        UserLogin creator = UserLogin.builder().id(senderId).role(Role.USER).build();
        Document doc = Document.builder().id(docId).creatorUser(creator).status(DocumentStatus.IN_REVIEW).build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(senderId)).thenReturn(Optional.of(creator));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));

        SubmitApprovalRequest req = SubmitApprovalRequest.builder().approverUserId(300L).build();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentApprovalService.submitForApproval(docId, req, senderId));
        assertEquals(ErrorCode.DOCUMENT_INVALID_STATUS, ex.getErrorCode());
    }

    @Test
    @DisplayName("Gửi duyệt thất bại khi không tìm thấy người duyệt (NOT_FOUND)")
    void submitForApproval_ApproverNotFound_ThrowsNotFound() {
        Long docId = 1L;
        Long senderId = 100L;
        Long approverId = 999L;
        UserLogin creator = UserLogin.builder().id(senderId).role(Role.USER).build();
        Document doc = Document.builder().id(docId).creatorUser(creator).status(DocumentStatus.DRAFT).title("Tiêu đề").summary("Trích yếu").content("ND").build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(senderId)).thenReturn(Optional.of(creator));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentAttachmentRepository.existsByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(docId)).thenReturn(true);
        when(userLoginRepository.findByIdAndDeleteFlgFalse(approverId)).thenReturn(Optional.empty());

        SubmitApprovalRequest req = SubmitApprovalRequest.builder().approverUserId(approverId).build();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentApprovalService.submitForApproval(docId, req, senderId));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Thu hồi thất bại khi không phải người tạo và không phải admin (FORBIDDEN)")
    void recallApproval_Forbidden_ThrowsForbidden() {
        Long docId = 1L;
        Long userId = 200L;
        UserLogin user = UserLogin.builder().id(userId).role(Role.USER).build();
        UserLogin creator = UserLogin.builder().id(100L).role(Role.USER).build();
        Document doc = Document.builder().id(docId).creatorUser(creator).status(DocumentStatus.IN_REVIEW).build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(user));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));

        RecallApprovalRequest req = RecallApprovalRequest.builder().recallReason("reason").build();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentApprovalService.recallApproval(docId, req, userId));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Thu hồi thất bại khi trạng thái văn bản không phải WAITING_APPROVAL (DOCUMENT_INVALID_STATUS)")
    void recallApproval_InvalidStatus_ThrowsInvalidStatus() {
        Long docId = 1L;
        Long userId = 100L;
        UserLogin creator = UserLogin.builder().id(userId).role(Role.USER).build();
        Document doc = Document.builder().id(docId).creatorUser(creator).status(DocumentStatus.DRAFT).build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(creator));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));

        RecallApprovalRequest req = RecallApprovalRequest.builder().recallReason("reason").build();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentApprovalService.recallApproval(docId, req, userId));
        assertEquals(ErrorCode.DOCUMENT_INVALID_STATUS, ex.getErrorCode());
    }

    @Test
    @DisplayName("Kiểm tra người duyệt đủ điều kiện với phân quyền từ PositionPermission")
    void isUserEligibleApprover_WithPositionPermission_Success() {
        Long userId = 77L;
        UserLogin user = UserLogin.builder().id(userId).role(Role.USER).build();

        vn.edu.epu.eoffice_be.entity.PositionPermission perm =
                vn.edu.epu.eoffice_be.entity.PositionPermission.builder()
                        .positionCd("TP")
                        .permissionCd("DOCUMENT_APPROVE")
                        .build();
        when(positionPermissionRepository.findByPositionCdInAndDeleteFlgFalse(any()))
                .thenReturn(java.util.List.of(perm));

        vn.edu.epu.eoffice_be.entity.PositionAssignment pa =
                vn.edu.epu.eoffice_be.entity.PositionAssignment.builder()
                        .id(20L)
                        .userLogin(user)
                        .positionCd("TP")
                        .deleteFlg(false)
                        .build();
        when(positionAssignmentRepository.findByUserLoginIdAndDeleteFlgFalse(userId))
                .thenReturn(java.util.List.of(pa));

        org.junit.jupiter.api.Assertions.assertTrue(documentApprovalService.isUserEligibleApprover(user));
    }

    @Test
    @DisplayName("Kiểm tra người duyệt không đủ điều kiện khi chức vụ đã bị bãi nhiệm (actualDismissalDate != null)")
    void isUserEligibleApprover_Dismissed_ReturnsFalse() {
        Long userId = 88L;
        UserLogin user = UserLogin.builder().id(userId).role(Role.USER).build();

        vn.edu.epu.eoffice_be.entity.PositionPermission perm =
                vn.edu.epu.eoffice_be.entity.PositionPermission.builder()
                        .positionCd("HT")
                        .permissionCd("DOCUMENT_APPROVE")
                        .deleteFlg(false)
                        .build();
        when(positionPermissionRepository.findByPositionCdInAndDeleteFlgFalse(any()))
                .thenReturn(java.util.List.of(perm));

        vn.edu.epu.eoffice_be.entity.PositionAssignment pa =
                vn.edu.epu.eoffice_be.entity.PositionAssignment.builder()
                        .id(21L)
                        .userLogin(user)
                        .positionCd("HT")
                        .actualDismissalDate(java.time.Instant.now().minusSeconds(3600))
                        .deleteFlg(false)
                        .build();
        when(positionAssignmentRepository.findByUserLoginIdAndDeleteFlgFalse(userId))
                .thenReturn(java.util.List.of(pa));

        org.junit.jupiter.api.Assertions.assertFalse(documentApprovalService.isUserEligibleApprover(user));
    }

    @Test
    @DisplayName("Gửi duyệt văn bản từ trạng thái REJECTED ghi nhật ký tiến trình fromStatus = REJECTED")
    void submitForApproval_FromRejectedStatus_LogsFromStatusAsRejected() {
        Long docId = 1L;
        Long senderId = 100L;
        Long approverId = 200L;

        UserLogin sender = UserLogin.builder().id(senderId).username("author").role(Role.USER).build();
        UserLogin approver = UserLogin.builder().id(approverId).username("dean").role(Role.ADMIN).build();

        Document doc = Document.builder()
                .id(docId)
                .draftCode("DT-2026-00002")
                .title("Kế hoạch bị từ chối sửa lại")
                .summary("Trích yếu")
                .content("Nội dung")
                .creatorUser(sender)
                .status(DocumentStatus.REJECTED)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(senderId)).thenReturn(Optional.of(sender));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentAttachmentRepository.existsByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(docId)).thenReturn(true);
        when(userLoginRepository.findByIdAndDeleteFlgFalse(approverId)).thenReturn(Optional.of(approver));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentDraftService.mapToDocumentResponse(any())).thenReturn(DocumentResponse.builder().id(docId).status(DocumentStatus.IN_REVIEW).build());

        SubmitApprovalRequest request = SubmitApprovalRequest.builder()
                .approverUserId(approverId)
                .submitNote("Đã sửa theo góp ý")
                .build();

        documentApprovalService.submitForApproval(docId, request, senderId);

        org.mockito.ArgumentCaptor<DocumentProcessLog> logCaptor = org.mockito.ArgumentCaptor.forClass(DocumentProcessLog.class);
        verify(documentProcessLogRepository).save(logCaptor.capture());
        DocumentProcessLog capturedLog = logCaptor.getValue();
        assertEquals("REJECTED", capturedLog.getFromStatus());
        assertEquals("IN_REVIEW", capturedLog.getToStatus());
        assertEquals(2, doc.getCurrentVersion());
    }

    @Test
    @DisplayName("Gửi duyệt lại từ REJECTED hoặc NEEDS_REVISION mà chưa tải tệp chính mới thì ném lỗi DOCUMENT_INCOMPLETE_DOSSIER")
    void submitForApproval_Resubmission_WithoutNewMainAttachment_ThrowsIncompleteDossier() {
        Long docId = 2L;
        Long senderId = 100L;
        Long approverId = 200L;

        UserLogin sender = UserLogin.builder().id(senderId).username("author").role(Role.USER).build();
        Document doc = Document.builder()
                .id(docId)
                .title("Kế hoạch")
                .summary("Trích yếu")
                .creatorUser(sender)
                .status(DocumentStatus.NEEDS_REVISION)
                .currentVersion(1)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(senderId)).thenReturn(Optional.of(sender));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        // Chưa có file chính mới
        when(documentAttachmentRepository.existsByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(docId)).thenReturn(false);

        SubmitApprovalRequest request = SubmitApprovalRequest.builder()
                .approverUserId(approverId)
                .submitNote("Gửi lại")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentApprovalService.submitForApproval(docId, request, senderId));
        assertEquals(ErrorCode.DOCUMENT_INCOMPLETE_DOSSIER, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Tệp PDF cũ đã bị đóng băng"));
    }

    @Test
    @DisplayName("Gửi duyệt lại từ NEEDS_REVISION khi đã tải tệp v2 thành công: tăng currentVersion từ 1 lên 2")
    void submitForApproval_Resubmission_WithNewMainAttachment_IncrementsVersion() {
        Long docId = 3L;
        Long senderId = 100L;
        Long approverId = 200L;

        UserLogin sender = UserLogin.builder().id(senderId).username("author").role(Role.USER).build();
        UserLogin approver = UserLogin.builder().id(approverId).username("dean").role(Role.ADMIN).build();

        Document doc = Document.builder()
                .id(docId)
                .title("Kế hoạch v2")
                .summary("Trích yếu v2")
                .creatorUser(sender)
                .status(DocumentStatus.NEEDS_REVISION)
                .currentVersion(1)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(senderId)).thenReturn(Optional.of(sender));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentAttachmentRepository.existsByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(docId)).thenReturn(true);
        when(userLoginRepository.findByIdAndDeleteFlgFalse(approverId)).thenReturn(Optional.of(approver));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentDraftService.mapToDocumentResponse(any())).thenReturn(DocumentResponse.builder().id(docId).status(DocumentStatus.IN_REVIEW).build());

        SubmitApprovalRequest request = SubmitApprovalRequest.builder()
                .approverUserId(approverId)
                .submitNote("Đã sửa nội dung và tải file v2")
                .build();

        documentApprovalService.submitForApproval(docId, request, senderId);

        assertEquals(2, doc.getCurrentVersion());
        assertEquals(DocumentStatus.IN_REVIEW, doc.getStatus());
    }

    @Test
    @DisplayName("Kiểm tra phạm vi đơn vị (org scoping): Cùng đơn vị hoặc đơn vị cha thì hợp lệ, đơn vị ngang hàng (sibling) bị từ chối")
    void isUserEligibleApprover_OrgScoping_ParentAndSameUnitEligible_SiblingIneligible() {
        OrgNode school = OrgNode.builder().id(1L).nameVn("Trường ĐH").build();
        OrgNode facultyIt = OrgNode.builder().id(10L).nameVn("Khoa CNTT").parent(school).build();
        OrgNode facultyEcon = OrgNode.builder().id(20L).nameVn("Khoa Kinh tế").parent(school).build();

        Document docIt = Document.builder().id(1L).issuingOrgNode(facultyIt).build();

        // 1. Cán bộ thuộc cùng Khoa CNTT
        UserLogin itLeader = UserLogin.builder().id(101L).role(Role.USER).build();
        PositionAssignment itPa = PositionAssignment.builder()
                .id(1L).userLogin(itLeader).positionCd("TK").orgNode(facultyIt).deleteFlg(false).build();
        when(positionAssignmentRepository.findByUserLoginIdAndDeleteFlgFalse(101L)).thenReturn(List.of(itPa));
        assertTrue(documentApprovalService.isUserEligibleApprover(itLeader, docIt));

        // 2. Cán bộ thuộc đơn vị cha (Trường - Hiệu trưởng)
        UserLogin schoolLeader = UserLogin.builder().id(102L).role(Role.USER).build();
        PositionAssignment schoolPa = PositionAssignment.builder()
                .id(2L).userLogin(schoolLeader).positionCd("HT").orgNode(school).deleteFlg(false).build();
        when(positionAssignmentRepository.findByUserLoginIdAndDeleteFlgFalse(102L)).thenReturn(List.of(schoolPa));
        assertTrue(documentApprovalService.isUserEligibleApprover(schoolLeader, docIt));

        // 3. Cán bộ thuộc đơn vị khác (Khoa Kinh tế - đơn vị ngang hàng)
        UserLogin econLeader = UserLogin.builder().id(103L).role(Role.USER).build();
        PositionAssignment econPa = PositionAssignment.builder()
                .id(3L).userLogin(econLeader).positionCd("TK").orgNode(facultyEcon).deleteFlg(false).build();
        when(positionAssignmentRepository.findByUserLoginIdAndDeleteFlgFalse(103L)).thenReturn(List.of(econPa));
        assertFalse(documentApprovalService.isUserEligibleApprover(econLeader, docIt));
    }

    @Test
    @DisplayName("Gửi duyệt thất bại khi người duyệt thuộc đơn vị khác không cùng phân cấp (APPROVER_NOT_ELIGIBLE)")
    void submitForApproval_CrossUnitApprover_ThrowsApproverNotEligible() {
        Long docId = 1L;
        Long senderId = 100L;
        Long crossUnitApproverId = 300L;

        OrgNode school = OrgNode.builder().id(1L).nameVn("Trường ĐH").build();
        OrgNode facultyIt = OrgNode.builder().id(10L).nameVn("Khoa CNTT").parent(school).build();
        OrgNode facultyEcon = OrgNode.builder().id(20L).nameVn("Khoa Kinh tế").parent(school).build();

        UserLogin sender = UserLogin.builder().id(senderId).username("author").role(Role.USER).build();
        UserLogin crossApprover = UserLogin.builder().id(crossUnitApproverId).username("econ_dean").role(Role.USER).build();

        Document doc = Document.builder()
                .id(docId)
                .title("Kế hoạch CNTT")
                .summary("Trích yếu")
                .content("Nội dung")
                .creatorUser(sender)
                .issuingOrgNode(facultyIt)
                .status(DocumentStatus.DRAFT)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(senderId)).thenReturn(Optional.of(sender));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentAttachmentRepository.existsByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(docId)).thenReturn(true);
        when(userLoginRepository.findByIdAndDeleteFlgFalse(crossUnitApproverId)).thenReturn(Optional.of(crossApprover));

        // Người duyệt thuộc Khoa Kinh tế
        PositionAssignment econPa = PositionAssignment.builder()
                .id(3L).userLogin(crossApprover).positionCd("TK").orgNode(facultyEcon).deleteFlg(false).build();
        when(positionAssignmentRepository.findByUserLoginIdAndDeleteFlgFalse(crossUnitApproverId)).thenReturn(List.of(econPa));

        SubmitApprovalRequest request = SubmitApprovalRequest.builder()
                .approverUserId(crossUnitApproverId)
                .build();

        assertThrows(BusinessException.class, () -> documentApprovalService.submitForApproval(docId, request, senderId));
    }

    @Test
    @DisplayName("Kiểm tra xử lý an toàn không lặp vô hạn khi cây đơn vị có chu trình cha (cycle protection)")
    void submitForApproval_OrgNodeCycle_HandlesCycleSafelyWithoutLoop() {
        Long docId = 1L;
        Long senderId = 100L;
        Long approverId = 200L;

        UserLogin sender = UserLogin.builder().id(senderId).role(Role.USER).build();
        UserLogin approver = UserLogin.builder().id(approverId).role(Role.USER).build();

        // Tạo 2 đơn vị có chu trình cha lặp vòng: nodeA -> nodeB -> nodeA
        OrgNode nodeA = OrgNode.builder().id(10L).nameVn("Đơn vị A").build();
        OrgNode nodeB = OrgNode.builder().id(20L).nameVn("Đơn vị B").build();
        nodeA.setParent(nodeB);
        nodeB.setParent(nodeA);

        Document doc = Document.builder()
                .id(docId)
                .title("Dự thảo kiểm tra chu trình")
                .summary("Trích yếu")
                .content("Nội dung")
                .creatorUser(sender)
                .issuingOrgNode(nodeA)
                .status(DocumentStatus.DRAFT)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(senderId)).thenReturn(Optional.of(sender));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentAttachmentRepository.existsByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(docId)).thenReturn(true);
        when(userLoginRepository.findByIdAndDeleteFlgFalse(approverId)).thenReturn(Optional.of(approver));

        // Approver thuộc nodeB (nằm trong chu trình của nodeA)
        PositionAssignment pa = PositionAssignment.builder()
                .id(1L)
                .userLogin(approver)
                .positionCd("TK")
                .orgNode(nodeB)
                .deleteFlg(false)
                .build();
        when(positionAssignmentRepository.findByUserLoginIdAndDeleteFlgFalse(approverId)).thenReturn(List.of(pa));
        when(documentRepository.save(any(Document.class))).thenReturn(doc);

        SubmitApprovalRequest request = SubmitApprovalRequest.builder()
                .approverUserId(approverId)
                .build();

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> documentApprovalService.submitForApproval(docId, request, senderId)
        );
    }
}
