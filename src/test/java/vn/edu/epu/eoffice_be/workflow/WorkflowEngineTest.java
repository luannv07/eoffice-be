package vn.edu.epu.eoffice_be.workflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentWorkflowLog;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentWorkflowLogRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentWorkflowLogRepository documentWorkflowLogRepository;

    @InjectMocks
    private WorkflowEngine workflowEngine;

    private UserLogin drafter;
    private OrgNode orgNode;
    private Document document;

    @BeforeEach
    void setUp() {
        drafter = UserLogin.builder().id(1001L).username("drafter").role(Role.USER).build();
        orgNode = OrgNode.builder().id(2001L).nameVn("Khoa CNTT").build();

        document = Document.builder()
                .id(5001L)
                .title("Du thao Ke hoach 2026")
                .documentTypeCd("KE_HOACH")
                .drafter(drafter)
                .issuingOrg(orgNode)
                .status(DocumentStatus.DRAFT)
                .build();
    }

    @Test
    @DisplayName("State Machine: DRAFT -> SUBMITTED khi gửi duyệt")
    void getNextStatus_fromDraft_submit_shouldReturnSubmitted() {
        DocumentStatus next = workflowEngine.getNextStatus(DocumentStatus.DRAFT, WorkflowAction.SUBMIT);
        assertEquals(DocumentStatus.SUBMITTED, next);
    }

    @Test
    @DisplayName("State Machine: SUBMITTED -> IN_REVIEW khi ký nháy hoặc duyệt sơ bộ")
    void getNextStatus_fromSubmitted_initialSign_shouldReturnInReview() {
        DocumentStatus next = workflowEngine.getNextStatus(DocumentStatus.SUBMITTED, WorkflowAction.INITIAL_SIGN);
        assertEquals(DocumentStatus.IN_REVIEW, next);
    }

    @Test
    @DisplayName("State Machine: IN_REVIEW -> LEADER_REVIEW khi duyệt chuyển cấp lãnh đạo")
    void getNextStatus_fromInReview_reviewApprove_shouldReturnLeaderReview() {
        DocumentStatus next = workflowEngine.getNextStatus(DocumentStatus.IN_REVIEW, WorkflowAction.REVIEW_APPROVE);
        assertEquals(DocumentStatus.LEADER_REVIEW, next);
    }

    @Test
    @DisplayName("State Machine: LEADER_REVIEW -> PUBLISHED khi ký phát hành")
    void getNextStatus_fromLeaderReview_signAndPublish_shouldReturnPublished() {
        DocumentStatus next = workflowEngine.getNextStatus(DocumentStatus.LEADER_REVIEW, WorkflowAction.SIGN_AND_PUBLISH);
        assertEquals(DocumentStatus.PUBLISHED, next);
    }

    @Test
    @DisplayName("State Machine: Chặn nhảy cóc trạng thái bất hợp lệ")
    void getNextStatus_invalidTransition_shouldThrowBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                workflowEngine.getNextStatus(DocumentStatus.DRAFT, WorkflowAction.SIGN_AND_PUBLISH));
        assertEquals(ErrorCode.WORKFLOW_TRANSITION_NOT_ALLOWED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Guards: Bắt buộc phải có lý do khi từ chối hoặc yêu cầu sửa đổi")
    void validateTransition_missingCommentOnReject_shouldThrowBadRequest() {
        document.setStatus(DocumentStatus.IN_REVIEW);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                workflowEngine.validateTransition(document, WorkflowAction.REJECT, "   "));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("Thực hiện chuyển đổi trạng thái thành công và ghi nhận Log")
    void executeTransition_success() {
        document.setStatus(DocumentStatus.DRAFT);

        when(documentRepository.save(any(Document.class))).thenReturn(document);
        when(documentWorkflowLogRepository.save(any(DocumentWorkflowLog.class))).thenAnswer(i -> i.getArgument(0));

        DocumentWorkflowLog log = workflowEngine.executeTransition(
                document,
                WorkflowAction.SUBMIT,
                drafter,
                "Gui duyet lan 1",
                null,
                null,
                null
        );

        assertNotNull(log);
        assertEquals(DocumentStatus.SUBMITTED, document.getStatus());
        assertEquals(DocumentStatus.DRAFT, log.getFromStatus());
        assertEquals(DocumentStatus.SUBMITTED, log.getToStatus());
        assertEquals(WorkflowAction.SUBMIT, log.getAction());

        verify(documentRepository).save(document);
        verify(documentWorkflowLogRepository).save(any(DocumentWorkflowLog.class));
    }

    @Test
    @DisplayName("Phân quyền: Chặn actor không phải drafter khi SUBMIT hoặc WITHDRAW")
    void validateActorPermission_unauthorizedDrafterActions() {
        UserLogin stranger = UserLogin.builder().id(9999L).username("stranger").role(Role.USER).build();

        BusinessException exSubmit = assertThrows(BusinessException.class, () ->
                workflowEngine.validateActorPermission(document, stranger, WorkflowAction.SUBMIT));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, exSubmit.getErrorCode());

        BusinessException exWithdraw = assertThrows(BusinessException.class, () ->
                workflowEngine.validateActorPermission(document, stranger, WorkflowAction.WITHDRAW));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, exWithdraw.getErrorCode());
    }

    @Test
    @DisplayName("Phân quyền: Chặn regular USER thực hiện REVIEW_APPROVE, INITIAL_SIGN, LEADER_APPROVE, REQUEST_REVISION, REJECT")
    void validateActorPermission_unauthorizedRegularUser_shouldThrow() {
        UserLogin regularUser = UserLogin.builder().id(2002L).username("regular").role(Role.USER).build();

        // REVIEW_APPROVE
        BusinessException exReview = assertThrows(BusinessException.class, () ->
                workflowEngine.validateActorPermission(document, regularUser, WorkflowAction.REVIEW_APPROVE));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, exReview.getErrorCode());

        // INITIAL_SIGN
        BusinessException exInitial = assertThrows(BusinessException.class, () ->
                workflowEngine.validateActorPermission(document, regularUser, WorkflowAction.INITIAL_SIGN));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, exInitial.getErrorCode());

        // LEADER_APPROVE
        BusinessException exLeader = assertThrows(BusinessException.class, () ->
                workflowEngine.validateActorPermission(document, regularUser, WorkflowAction.LEADER_APPROVE));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, exLeader.getErrorCode());

        // REQUEST_REVISION
        BusinessException exRevision = assertThrows(BusinessException.class, () ->
                workflowEngine.validateActorPermission(document, regularUser, WorkflowAction.REQUEST_REVISION));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, exRevision.getErrorCode());

        // REJECT
        BusinessException exReject = assertThrows(BusinessException.class, () ->
                workflowEngine.validateActorPermission(document, regularUser, WorkflowAction.REJECT));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, exReject.getErrorCode());
    }

    @Test
    @DisplayName("Phân quyền: Cho phép actor có role REVIEWER hoặc LEADER hoặc được gán ký")
    void validateActorPermission_authorizedRoles_shouldPass() {
        UserLogin reviewer = UserLogin.builder().id(3001L).username("reviewer").role(Role.REVIEWER).build();
        UserLogin leaderUser = UserLogin.builder().id(3002L).username("leader").role(Role.LEADER).build();
        UserLogin assignedSigner = UserLogin.builder().id(3003L).username("signer").role(Role.USER).build();
        document.setSigner(assignedSigner);

        // REVIEWER có thể REVIEW_APPROVE, INITIAL_SIGN, REQUEST_REVISION, REJECT
        assertDoesNotThrow(() -> workflowEngine.validateActorPermission(document, reviewer, WorkflowAction.REVIEW_APPROVE));
        assertDoesNotThrow(() -> workflowEngine.validateActorPermission(document, reviewer, WorkflowAction.INITIAL_SIGN));
        assertDoesNotThrow(() -> workflowEngine.validateActorPermission(document, reviewer, WorkflowAction.REQUEST_REVISION));
        assertDoesNotThrow(() -> workflowEngine.validateActorPermission(document, reviewer, WorkflowAction.REJECT));

        // LEADER có thể LEADER_APPROVE, SIGN_AND_PUBLISH (khi là leader), REVIEW_APPROVE, v.v.
        assertDoesNotThrow(() -> workflowEngine.validateActorPermission(document, leaderUser, WorkflowAction.LEADER_APPROVE));

        // Assigned signer có thể LEADER_APPROVE, SIGN_AND_PUBLISH
        assertDoesNotThrow(() -> workflowEngine.validateActorPermission(document, assignedSigner, WorkflowAction.SIGN_AND_PUBLISH));
        assertDoesNotThrow(() -> workflowEngine.validateActorPermission(document, assignedSigner, WorkflowAction.LEADER_APPROVE));
    }

    @Test
    @DisplayName("Phân quyền: Chặn SIGN_AND_PUBLISH khi actor không phải signer được gán và không phải LEADER")
    void validateActorPermission_unauthorizedSignAndPublish() {
        UserLogin assignedSigner = UserLogin.builder().id(3003L).username("signer").role(Role.USER).build();
        document.setSigner(assignedSigner);

        UserLogin otherUser = UserLogin.builder().id(4004L).username("other").role(Role.REVIEWER).build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                workflowEngine.validateActorPermission(document, otherUser, WorkflowAction.SIGN_AND_PUBLISH));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, ex.getErrorCode());
    }

    @Test
    @DisplayName("Phân quyền: Quản trị viên (ADMIN, SYSTEM_ADMIN) bypass kiểm tra thẩm quyền")
    void validateActorPermission_adminBypass() {
        UserLogin admin = UserLogin.builder().id(8888L).username("admin").role(Role.ADMIN).build();
        UserLogin sysAdmin = UserLogin.builder().id(9999L).username("sysadmin").role(Role.SYSTEM_ADMIN).build();

        assertDoesNotThrow(() -> workflowEngine.validateActorPermission(document, admin, WorkflowAction.SUBMIT));
        assertDoesNotThrow(() -> workflowEngine.validateActorPermission(document, admin, WorkflowAction.REVIEW_APPROVE));
        assertDoesNotThrow(() -> workflowEngine.validateActorPermission(document, admin, WorkflowAction.LEADER_APPROVE));
        assertDoesNotThrow(() -> workflowEngine.validateActorPermission(document, admin, WorkflowAction.SIGN_AND_PUBLISH));
        assertDoesNotThrow(() -> workflowEngine.validateActorPermission(document, sysAdmin, WorkflowAction.REJECT));
    }
}
