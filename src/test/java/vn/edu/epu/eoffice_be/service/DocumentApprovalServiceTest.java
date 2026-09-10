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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private DocumentApprovalService documentApprovalService;

    @Test
    @DisplayName("Gửi văn bản để duyệt thành công (UC11 - Luồng 2.1)")
    void submitForApproval_Success() {
        Long docId = 1L;
        Long senderId = 100L;
        Long approverId = 200L;

        UserLogin sender = UserLogin.builder().id(senderId).username("author").role(Role.USER).build();
        UserLogin approver = UserLogin.builder().id(approverId).username("dean").role(Role.USER).build();

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
        when(documentDraftService.mapToDocumentResponse(any())).thenReturn(DocumentResponse.builder().id(docId).status(DocumentStatus.WAITING_APPROVAL).build());

        SubmitApprovalRequest request = SubmitApprovalRequest.builder()
                .approverUserId(approverId)
                .submitNote("Kính gửi thầy duyệt giúp")
                .build();

        DocumentResponse response = documentApprovalService.submitForApproval(docId, request, senderId);

        assertNotNull(response);
        assertEquals(DocumentStatus.WAITING_APPROVAL, doc.getStatus());
        assertNotNull(doc.getSubmittedAt());

        verify(documentApprovalRequestRepository).save(any(DocumentApprovalRequest.class));
        verify(documentProcessLogRepository).save(any(DocumentProcessLog.class));
        verify(eventPublisher).publishEvent(any(DocumentSubmittedEvent.class));
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
                .status(DocumentStatus.WAITING_APPROVAL)
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
                .status(DocumentStatus.WAITING_APPROVAL)
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
    @DisplayName("Kiểm tra DocumentApprovalRequest no-args constructor khởi tạo status và submittedAt không null")
    void documentApprovalRequest_NoArgsConstructor_InitializesDefaults() {
        DocumentApprovalRequest req = new DocumentApprovalRequest();
        assertNotNull(req.getStatus());
        assertEquals(ApprovalRequestStatus.PENDING, req.getStatus());
        assertNotNull(req.getSubmittedAt());
        org.junit.jupiter.api.Assertions.assertFalse(req.isDeleteFlg());

        // Kiểm tra @PrePersist
        DocumentApprovalRequest empty = new DocumentApprovalRequest();
        empty.setStatus(null);
        empty.setSubmittedAt(null);
        empty.prePersist();
        assertEquals(ApprovalRequestStatus.PENDING, empty.getStatus());
        assertNotNull(empty.getSubmittedAt());
    }
}
