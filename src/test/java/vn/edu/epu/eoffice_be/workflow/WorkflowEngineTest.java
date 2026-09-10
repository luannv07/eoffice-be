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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private UserLogin leader;
    private OrgNode orgNode;
    private Document document;

    @BeforeEach
    void setUp() {
        drafter = UserLogin.builder().id(1001L).username("drafter").role(Role.USER).build();
        leader = UserLogin.builder().id(1002L).username("leader").role(Role.ADMIN).build();
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
}
