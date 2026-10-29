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
import vn.edu.epu.eoffice_be.service.PermissionResolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentWorkflowLogRepository documentWorkflowLogRepository;

    @Mock
    private PermissionResolver permissionResolver;

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

    // =========================================================================
    // 1. STATE MACHINE & TRANSITION MATRIX TESTS
    // =========================================================================

    @Test
    @DisplayName("State Machine: DRAFT -> IN_REVIEW khi gửi duyệt (SUBMIT)")
    void getNextStatus_fromDraft_submit_shouldReturnInReview() {
        DocumentStatus next = workflowEngine.getNextStatus(DocumentStatus.DRAFT, WorkflowAction.SUBMIT);
        assertEquals(DocumentStatus.IN_REVIEW, next);
    }

    @Test
    @DisplayName("State Machine: IN_REVIEW transitions")
    void getNextStatus_fromInReview_transitions_shouldSucceed() {
        assertEquals(DocumentStatus.IN_REVIEW, workflowEngine.getNextStatus(DocumentStatus.IN_REVIEW, WorkflowAction.INITIAL_SIGN));
        assertEquals(DocumentStatus.PENDING_APPROVAL, workflowEngine.getNextStatus(DocumentStatus.IN_REVIEW, WorkflowAction.REVIEW_APPROVE));
        assertEquals(DocumentStatus.NEEDS_REVISION, workflowEngine.getNextStatus(DocumentStatus.IN_REVIEW, WorkflowAction.REQUEST_REVISION));
        assertEquals(DocumentStatus.REJECTED, workflowEngine.getNextStatus(DocumentStatus.IN_REVIEW, WorkflowAction.REJECT));
        assertEquals(DocumentStatus.DRAFT, workflowEngine.getNextStatus(DocumentStatus.IN_REVIEW, WorkflowAction.WITHDRAW));
    }

    @Test
    @DisplayName("State Machine: PENDING_APPROVAL -> APPROVED khi lãnh đạo phê duyệt")
    void getNextStatus_fromPendingApproval_leaderApprove_shouldReturnApproved() {
        DocumentStatus next = workflowEngine.getNextStatus(DocumentStatus.PENDING_APPROVAL, WorkflowAction.LEADER_APPROVE);
        assertEquals(DocumentStatus.APPROVED, next);
        assertEquals(DocumentStatus.NEEDS_REVISION, workflowEngine.getNextStatus(DocumentStatus.PENDING_APPROVAL, WorkflowAction.REQUEST_REVISION));
        assertEquals(DocumentStatus.REJECTED, workflowEngine.getNextStatus(DocumentStatus.PENDING_APPROVAL, WorkflowAction.REJECT));
    }

    @Test
    @DisplayName("State Machine: APPROVED -> PUBLISHED khi ký phát hành")
    void getNextStatus_fromApproved_signAndPublish_shouldReturnPublished() {
        DocumentStatus next = workflowEngine.getNextStatus(DocumentStatus.APPROVED, WorkflowAction.SIGN_AND_PUBLISH);
        assertEquals(DocumentStatus.PUBLISHED, next);
        assertEquals(DocumentStatus.NEEDS_REVISION, workflowEngine.getNextStatus(DocumentStatus.APPROVED, WorkflowAction.REQUEST_REVISION));
    }

    @Test
    @DisplayName("State Machine: PUBLISHED -> REVOKED khi thu hồi văn bản")
    void getNextStatus_fromPublished_revoke_shouldReturnRevoked() {
        DocumentStatus next = workflowEngine.getNextStatus(DocumentStatus.PUBLISHED, WorkflowAction.REVOKE);
        assertEquals(DocumentStatus.REVOKED, next);
    }

    @Test
    @DisplayName("State Machine: REJECTED transitions (SUBMIT -> IN_REVIEW, WITHDRAW -> DRAFT)")
    void getNextStatus_fromRejected_transitions_shouldSucceed() {
        assertEquals(DocumentStatus.IN_REVIEW, workflowEngine.getNextStatus(DocumentStatus.REJECTED, WorkflowAction.SUBMIT));
        assertEquals(DocumentStatus.DRAFT, workflowEngine.getNextStatus(DocumentStatus.REJECTED, WorkflowAction.WITHDRAW));
    }

    @Test
    @DisplayName("State Machine: Chặn chuyển trạng thái không nằm trong TRANSITION_MATRIX")
    void getNextStatus_invalidTransition_shouldThrowBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                workflowEngine.getNextStatus(DocumentStatus.DRAFT, WorkflowAction.SIGN_AND_PUBLISH));
        assertEquals(ErrorCode.WORKFLOW_TRANSITION_NOT_ALLOWED, ex.getErrorCode());
    }

    // =========================================================================
    // 2. GUARDS & LOGS TESTS
    // =========================================================================

    @Test
    @DisplayName("Guards: REQUEST_REVISION, REJECT, REVOKE thiếu comment phải bị từ chối (BAD_REQUEST)")
    void validateTransition_missingComment_shouldThrowBadRequest() {
        document.setStatus(DocumentStatus.IN_REVIEW);

        // REJECT thiếu comment
        BusinessException exReject = assertThrows(BusinessException.class, () ->
                workflowEngine.validateTransition(document, WorkflowAction.REJECT, "   "));
        assertEquals(ErrorCode.BAD_REQUEST, exReject.getErrorCode());

        // REQUEST_REVISION thiếu comment
        BusinessException exRev = assertThrows(BusinessException.class, () ->
                workflowEngine.validateTransition(document, WorkflowAction.REQUEST_REVISION, null));
        assertEquals(ErrorCode.BAD_REQUEST, exRev.getErrorCode());

        // REVOKE thiếu comment
        document.setStatus(DocumentStatus.PUBLISHED);
        BusinessException exRevoke = assertThrows(BusinessException.class, () ->
                workflowEngine.validateTransition(document, WorkflowAction.REVOKE, ""));
        assertEquals(ErrorCode.BAD_REQUEST, exRevoke.getErrorCode());
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
        assertEquals(DocumentStatus.IN_REVIEW, document.getStatus());
        assertEquals(DocumentStatus.DRAFT, log.getFromStatus());
        assertEquals(DocumentStatus.IN_REVIEW, log.getToStatus());
        assertEquals(WorkflowAction.SUBMIT, log.getAction());

        verify(documentRepository).save(document);
        verify(documentWorkflowLogRepository).save(any(DocumentWorkflowLog.class));
    }

    // =========================================================================
    // 3. PERMISSION & AUTHORIZATION TESTS (THE MATRIX)
    // =========================================================================

    @Test
    @DisplayName("Phân quyền: SUBMIT / WITHDRAW chỉ người soạn thảo (isDrafter), người khác bị từ chối")
    void validateActorPermission_drafterActions() {
        UserLogin stranger = UserLogin.builder().id(9999L).username("stranger").role(Role.USER).build();

        // Drafter hợp lệ
        assertDoesNotThrow(() -> workflowEngine.validateActorPermission(document, drafter, WorkflowAction.SUBMIT));
        assertDoesNotThrow(() -> workflowEngine.validateActorPermission(document, drafter, WorkflowAction.WITHDRAW));

        // Người lạ bị từ chối
        BusinessException exSubmit = assertThrows(BusinessException.class, () ->
                workflowEngine.validateActorPermission(document, stranger, WorkflowAction.SUBMIT));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, exSubmit.getErrorCode());

        BusinessException exWithdraw = assertThrows(BusinessException.class, () ->
                workflowEngine.validateActorPermission(document, stranger, WorkflowAction.WITHDRAW));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, exWithdraw.getErrorCode());
    }

    @Test
    @DisplayName("Phân quyền: SYSTEM_ADMIN không có permission nghiệp vụ -> Bị từ chối hành động workflow")
    void validateActorPermission_systemAdminWithoutBusinessPerm_shouldBeDenied() {
        UserLogin sysAdmin = UserLogin.builder().id(8888L).username("sysadmin").role(Role.SYSTEM_ADMIN).build();
        UserLogin admin = UserLogin.builder().id(8889L).username("admin").role(Role.ADMIN).build();

        // Giả lập PermissionResolver trả về false vì không có chức vụ hay quyền ký duyệt



        // SYSTEM_ADMIN bị từ chối
        BusinessException exSys = assertThrows(BusinessException.class, () ->
                workflowEngine.validateActorPermission(document, sysAdmin, WorkflowAction.REVIEW_APPROVE));
        assertEquals(ErrorCode.FORBIDDEN, exSys.getErrorCode());

        // ADMIN bị từ chối
        BusinessException exAdmin = assertThrows(BusinessException.class, () ->
                workflowEngine.validateActorPermission(document, admin, WorkflowAction.LEADER_APPROVE));
        assertEquals(ErrorCode.FORBIDDEN, exAdmin.getErrorCode());
    }

    @Test
    @DisplayName("Phân quyền: Người có quyền ở đơn vị cấp trên với inheritToChildren = true -> Được phép trong phạm vi kế thừa hợp lệ")
    void validateActorPermission_inheritedPermission_shouldPass() {
        UserLogin dean = UserLogin.builder().id(2002L).username("hieutruong").role(Role.USER).build();

        // Giả lập PermissionResolver trả về true do kế thừa hợp lệ tại đơn vị ban hành của văn bản (2001L)
        when(permissionResolver.hasPermission(dean.getId(), dean.getRole(), "DOCUMENT_APPROVE", 2001L))
                .thenReturn(true);

        assertDoesNotThrow(() -> workflowEngine.validateActorPermission(document, dean, WorkflowAction.LEADER_APPROVE));
        assertDoesNotThrow(() -> workflowEngine.validateActorPermission(document, dean, WorkflowAction.REVIEW_APPROVE));
    }

    @Test
    @DisplayName("Phân quyền: Người có quyền ở đơn vị khác, không có kế thừa hoặc ủy quyền hợp lệ -> Bị từ chối")
    void validateActorPermission_otherOrgWithoutInheritanceOrDelegation_shouldBeDenied() {
        UserLogin otherOrgLeader = UserLogin.builder().id(3003L).username("truongkhoakhac").role(Role.USER).build();

        // Không có quyền tại 2001L
        when(permissionResolver.hasPermission(otherOrgLeader.getId(), otherOrgLeader.getRole(), "DOCUMENT_APPROVE", 2001L))
                .thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                workflowEngine.validateActorPermission(document, otherOrgLeader, WorkflowAction.LEADER_APPROVE));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, ex.getErrorCode());
    }

    @Test
    @DisplayName("Phân quyền: Người ký được chỉ định nhưng không có DOCUMENT_SIGN_OFFICIAL -> Bị từ chối ký phát hành")
    void validateActorPermission_assignedSignerWithoutOfficialSignPerm_shouldBeDenied() {
        UserLogin signer = UserLogin.builder().id(4001L).username("assignedSigner").role(Role.USER).build();
        document.setSigner(signer);

        // Là đúng người ký được chỉ định, nhưng không có quyền DOCUMENT_SIGN_OFFICIAL trong phạm vi 2001L
        when(permissionResolver.hasPermission(signer.getId(), signer.getRole(), "DOCUMENT_SIGN_OFFICIAL", 2001L))
                .thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                workflowEngine.validateActorPermission(document, signer, WorkflowAction.SIGN_AND_PUBLISH));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, ex.getErrorCode());
    }

    @Test
    @DisplayName("Phân quyền: Người ký được chỉ định VÀ có DOCUMENT_SIGN_OFFICIAL -> Được phép ký phát hành")
    void validateActorPermission_assignedSignerWithOfficialSignPerm_shouldPass() {
        UserLogin signer = UserLogin.builder().id(4001L).username("assignedSigner").role(Role.USER).build();
        document.setSigner(signer);

        when(permissionResolver.hasPermission(signer.getId(), signer.getRole(), "DOCUMENT_SIGN_OFFICIAL", 2001L))
                .thenReturn(true);

        assertDoesNotThrow(() -> workflowEngine.validateActorPermission(document, signer, WorkflowAction.SIGN_AND_PUBLISH));
    }

    @Test
    @DisplayName("Phân quyền: Có DOCUMENT_SIGN_DRAFT nhưng không có DOCUMENT_APPROVE -> Được INITIAL_SIGN nhưng bị từ chối REQUEST_REVISION và REJECT")
    void validateActorPermission_hasDraftSignButNotApprove_shouldOnlyPassInitialSign() {
        UserLogin reviewer = UserLogin.builder().id(5001L).username("reviewer").role(Role.USER).build();

        when(permissionResolver.hasPermission(reviewer.getId(), reviewer.getRole(), "DOCUMENT_SIGN_DRAFT", 2001L))
                .thenReturn(true);
        when(permissionResolver.hasPermission(reviewer.getId(), reviewer.getRole(), "DOCUMENT_APPROVE", 2001L))
                .thenReturn(false);

        // Ký nháy: Được phép
        assertDoesNotThrow(() -> workflowEngine.validateActorPermission(document, reviewer, WorkflowAction.INITIAL_SIGN));

        // Trả về sửa: Bị từ chối
        BusinessException exRevision = assertThrows(BusinessException.class, () ->
                workflowEngine.validateActorPermission(document, reviewer, WorkflowAction.REQUEST_REVISION));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, exRevision.getErrorCode());

        // Từ chối: Bị từ chối
        BusinessException exReject = assertThrows(BusinessException.class, () ->
                workflowEngine.validateActorPermission(document, reviewer, WorkflowAction.REJECT));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, exReject.getErrorCode());
    }

    @Test
    @DisplayName("Phân quyền: document.getOrgId() == null -> Các hành động cần phạm vi đơn vị bị từ chối")
    void validateActorPermission_nullDocumentOrgId_shouldRejectOrgScopedActions() {
        // Văn bản không có đơn vị ban hành
        document.setIssuingOrg(null);
        document.setIssuingOrgNode(null);
        assertNull(document.getOrgId());

        UserLogin actor = UserLogin.builder().id(6001L).username("anyActor").role(Role.USER).build();

        // 1. SUBMIT và WITHDRAW của drafter vẫn được phép
        assertDoesNotThrow(() -> workflowEngine.validateActorPermission(document, drafter, WorkflowAction.SUBMIT));
        assertDoesNotThrow(() -> workflowEngine.validateActorPermission(document, drafter, WorkflowAction.WITHDRAW));

        // 2. Các hành động cần phạm vi đơn vị đều bị từ chối ngay lập tức
        WorkflowAction[] orgScopedActions = {
                WorkflowAction.INITIAL_SIGN,
                WorkflowAction.REVIEW_APPROVE,
                WorkflowAction.LEADER_APPROVE,
                WorkflowAction.REQUEST_REVISION,
                WorkflowAction.REJECT,
                WorkflowAction.SIGN_AND_PUBLISH,
                WorkflowAction.REVOKE
        };

        for (WorkflowAction action : orgScopedActions) {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    workflowEngine.validateActorPermission(document, actor, action));
            assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, ex.getErrorCode(),
                    "Hành động [" + action + "] phải bị từ chối khi document.getOrgId() == null");
        }
    }

    @Test
    @DisplayName("Phân quyền: Kiểm tra null actor hoặc null document")
    void validateActorPermission_nullActorOrDocument_shouldThrowException() {
        BusinessException exActor = assertThrows(BusinessException.class, () ->
                workflowEngine.validateActorPermission(document, null, WorkflowAction.SUBMIT));
        assertEquals(ErrorCode.UNAUTHORIZED, exActor.getErrorCode());

        BusinessException exDoc = assertThrows(BusinessException.class, () ->
                workflowEngine.validateActorPermission(null, drafter, WorkflowAction.SUBMIT));
        assertEquals(ErrorCode.DOCUMENT_NOT_FOUND, exDoc.getErrorCode());
    }

    @Test
    @DisplayName("Phân quyền: Chặn SUBMIT / WITHDRAW khi document.getDrafter() null mà không gây NullPointerException")
    void validateActorPermission_nullDrafter_shouldThrowUnauthorizedWorkflowAction() {
        document.setDrafter(null);

        BusinessException exSubmit = assertThrows(BusinessException.class, () ->
                workflowEngine.validateActorPermission(document, drafter, WorkflowAction.SUBMIT));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, exSubmit.getErrorCode());

        BusinessException exWithdraw = assertThrows(BusinessException.class, () ->
                workflowEngine.validateActorPermission(document, drafter, WorkflowAction.WITHDRAW));
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION, exWithdraw.getErrorCode());
    }
}
