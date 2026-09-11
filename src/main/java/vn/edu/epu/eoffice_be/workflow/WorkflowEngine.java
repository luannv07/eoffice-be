package vn.edu.epu.eoffice_be.workflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
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

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    private final DocumentRepository documentRepository;
    private final DocumentWorkflowLogRepository documentWorkflowLogRepository;

    private static final Map<DocumentStatus, Map<WorkflowAction, DocumentStatus>> TRANSITION_MATRIX =
            new EnumMap<>(DocumentStatus.class);

    static {
        // DRAFT
        Map<WorkflowAction, DocumentStatus> draftTransitions = new EnumMap<>(WorkflowAction.class);
        draftTransitions.put(WorkflowAction.SUBMIT, DocumentStatus.SUBMITTED);
        TRANSITION_MATRIX.put(DocumentStatus.DRAFT, draftTransitions);

        // SUBMITTED
        Map<WorkflowAction, DocumentStatus> submittedTransitions = new EnumMap<>(WorkflowAction.class);
        submittedTransitions.put(WorkflowAction.REVIEW_APPROVE, DocumentStatus.IN_REVIEW);
        submittedTransitions.put(WorkflowAction.INITIAL_SIGN, DocumentStatus.IN_REVIEW);
        submittedTransitions.put(WorkflowAction.WITHDRAW, DocumentStatus.DRAFT);
        submittedTransitions.put(WorkflowAction.REQUEST_REVISION, DocumentStatus.NEEDS_REVISION);
        submittedTransitions.put(WorkflowAction.REJECT, DocumentStatus.REJECTED);
        TRANSITION_MATRIX.put(DocumentStatus.SUBMITTED, submittedTransitions);

        // IN_REVIEW
        Map<WorkflowAction, DocumentStatus> inReviewTransitions = new EnumMap<>(WorkflowAction.class);
        inReviewTransitions.put(WorkflowAction.INITIAL_SIGN, DocumentStatus.IN_REVIEW);
        inReviewTransitions.put(WorkflowAction.REVIEW_APPROVE, DocumentStatus.LEADER_REVIEW);
        inReviewTransitions.put(WorkflowAction.LEADER_APPROVE, DocumentStatus.APPROVED);
        inReviewTransitions.put(WorkflowAction.REQUEST_REVISION, DocumentStatus.NEEDS_REVISION);
        inReviewTransitions.put(WorkflowAction.REJECT, DocumentStatus.REJECTED);
        TRANSITION_MATRIX.put(DocumentStatus.IN_REVIEW, inReviewTransitions);

        // LEADER_REVIEW
        Map<WorkflowAction, DocumentStatus> leaderReviewTransitions = new EnumMap<>(WorkflowAction.class);
        leaderReviewTransitions.put(WorkflowAction.LEADER_APPROVE, DocumentStatus.APPROVED);
        leaderReviewTransitions.put(WorkflowAction.SIGN_AND_PUBLISH, DocumentStatus.PUBLISHED);
        leaderReviewTransitions.put(WorkflowAction.REQUEST_REVISION, DocumentStatus.NEEDS_REVISION);
        leaderReviewTransitions.put(WorkflowAction.REJECT, DocumentStatus.REJECTED);
        TRANSITION_MATRIX.put(DocumentStatus.LEADER_REVIEW, leaderReviewTransitions);

        // APPROVED
        Map<WorkflowAction, DocumentStatus> approvedTransitions = new EnumMap<>(WorkflowAction.class);
        approvedTransitions.put(WorkflowAction.SIGN_AND_PUBLISH, DocumentStatus.PUBLISHED);
        approvedTransitions.put(WorkflowAction.REJECT, DocumentStatus.REJECTED);
        TRANSITION_MATRIX.put(DocumentStatus.APPROVED, approvedTransitions);

        // NEEDS_REVISION
        Map<WorkflowAction, DocumentStatus> needsRevisionTransitions = new EnumMap<>(WorkflowAction.class);
        needsRevisionTransitions.put(WorkflowAction.SUBMIT, DocumentStatus.SUBMITTED);
        needsRevisionTransitions.put(WorkflowAction.WITHDRAW, DocumentStatus.DRAFT);
        TRANSITION_MATRIX.put(DocumentStatus.NEEDS_REVISION, needsRevisionTransitions);
    }

    /**
     * Xác định trạng thái đích tiếp theo của văn bản khi thực hiện hành động.
     */
    public DocumentStatus getNextStatus(DocumentStatus currentStatus, WorkflowAction action) {
        Map<WorkflowAction, DocumentStatus> allowedActions = TRANSITION_MATRIX.get(currentStatus);
        if (allowedActions == null || !allowedActions.containsKey(action)) {
            log.warn("Chuyen doi trang thai khong hop le tu [{}] voi hanh dong [{}]", currentStatus, action);
            throw new BusinessException(ErrorCode.WORKFLOW_TRANSITION_NOT_ALLOWED);
        }
        return allowedActions.get(action);
    }

    /**
     * Kiểm tra tính hợp lệ của hành động và các tham số điều kiện (Guards).
     */
    public void validateTransition(Document document, WorkflowAction action, String comment) {
        if (document == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        // 1. Kiểm tra trạng thái có cho phép action không
        getNextStatus(document.getStatus(), action);

        // 2. Yêu cầu bắt buộc lý do khi từ chối hoặc yêu cầu sửa đổi
        if ((action == WorkflowAction.REQUEST_REVISION || action == WorkflowAction.REJECT)
                && (comment == null || comment.trim().isBlank())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    /**
     * Kiểm tra thẩm quyền của người thực hiện trên văn bản.
     */
    public void validateActorPermission(Document document, UserLogin actor, WorkflowAction action) {
        if (actor == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (document == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        if (actor.getRole() == Role.SYSTEM_ADMIN || actor.getRole() == Role.ADMIN) {
            return; // Quản trị viên có toàn quyền điều phối
        }

        boolean isDrafter = document.getDrafter() != null && Objects.equals(document.getDrafter().getId(), actor.getId());
        boolean isAssignedSigner = document.getSigner() != null && Objects.equals(document.getSigner().getId(), actor.getId());

        switch (action) {
            case SUBMIT, WITHDRAW -> {
                if (!isDrafter) {
                    throw new BusinessException(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION);
                }
            }
            case REVIEW_APPROVE, INITIAL_SIGN -> {
                if (actor.getRole() != Role.REVIEWER && actor.getRole() != Role.LEADER && !isAssignedSigner) {
                    throw new BusinessException(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION);
                }
            }
            case LEADER_APPROVE -> {
                if (actor.getRole() != Role.LEADER && !isAssignedSigner) {
                    throw new BusinessException(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION);
                }
            }
            case SIGN_AND_PUBLISH -> {
                if (!isAssignedSigner) {
                    throw new BusinessException(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION);
                }
            }
            case REQUEST_REVISION, REJECT -> {
                if (actor.getRole() != Role.REVIEWER && actor.getRole() != Role.LEADER && !isAssignedSigner) {
                    throw new BusinessException(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION);
                }
            }
            default -> throw new BusinessException(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION);
        }
    }

    /**
     * Thực hiện chuyển đổi trạng thái, cập nhật văn bản và ghi log luồng duyệt.
     */
    @Transactional
    public DocumentWorkflowLog executeTransition(
            Document document,
            WorkflowAction action,
            UserLogin actor,
            String comment,
            String leadershipInstruction,
            OrgNode assignedOrgNode,
            String signatureHash
    ) {
        validateTransition(document, action, comment);
        validateActorPermission(document, actor, action);

        DocumentStatus fromStatus = document.getStatus();
        DocumentStatus toStatus = getNextStatus(fromStatus, action);

        // Cập nhật trạng thái văn bản
        document.setStatus(toStatus);
        documentRepository.save(document);

        // Ghi vết lịch sử luồng duyệt (DocumentWorkflowLog)
        DocumentWorkflowLog workflowLog = DocumentWorkflowLog.builder()
                .document(document)
                .action(action)
                .actor(actor)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .comment(comment)
                .leadershipInstruction(leadershipInstruction)
                .assignedOrgNode(assignedOrgNode)
                .signatureHash(signatureHash)
                .deleteFlg(false)
                .build();

        DocumentWorkflowLog savedLog = documentWorkflowLogRepository.save(workflowLog);

        log.info("Chuyen trang thai van ban ID=[{}] thanh cong: [{}] -> [{}] boi User ID=[{}]",
                document.getId(), fromStatus, toStatus, actor.getId());

        return savedLog;
    }
}
