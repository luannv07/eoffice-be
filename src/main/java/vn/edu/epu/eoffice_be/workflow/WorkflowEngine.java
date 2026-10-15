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
         // 1. DRAFT (Dự thảo): Gửi duyệt sẽ chuyển sang IN_REVIEW
        Map<WorkflowAction, DocumentStatus> draftTransitions = new EnumMap<>(WorkflowAction.class);
        draftTransitions.put(WorkflowAction.SUBMIT, DocumentStatus.IN_REVIEW);
        TRANSITION_MATRIX.put(DocumentStatus.DRAFT, draftTransitions);
        // 2. IN_REVIEW (Đang thẩm định & ký nháy)
        Map<WorkflowAction, DocumentStatus> inReviewTransitions = new EnumMap<>(WorkflowAction.class);
        inReviewTransitions.put(WorkflowAction.INITIAL_SIGN, DocumentStatus.IN_REVIEW); // Ký nháy từng người, vẫn ở IN_REVIEW
        inReviewTransitions.put(WorkflowAction.REVIEW_APPROVE, DocumentStatus.PENDING_APPROVAL); // Ký xong hết / Duyệt chuyển tiếp lên Lãnh đạo
        inReviewTransitions.put(WorkflowAction.REQUEST_REVISION, DocumentStatus.NEEDS_REVISION); // Trả về sửa
        inReviewTransitions.put(WorkflowAction.REJECT, DocumentStatus.REJECTED); // Từ chối
        inReviewTransitions.put(WorkflowAction.WITHDRAW, DocumentStatus.DRAFT); // Rút lại bản nháp
        TRANSITION_MATRIX.put(DocumentStatus.IN_REVIEW, inReviewTransitions);
        // 3. PENDING_APPROVAL (Chờ lãnh đạo phê duyệt)
        Map<WorkflowAction, DocumentStatus> pendingApprovalTransitions = new EnumMap<>(WorkflowAction.class);
        pendingApprovalTransitions.put(WorkflowAction.LEADER_APPROVE, DocumentStatus.APPROVED); // Lãnh đạo duyệt nội dung
        pendingApprovalTransitions.put(WorkflowAction.REQUEST_REVISION, DocumentStatus.NEEDS_REVISION); // Lãnh đạo yêu cầu sửa
        pendingApprovalTransitions.put(WorkflowAction.REJECT, DocumentStatus.REJECTED); // Lãnh đạo bác bỏ
        TRANSITION_MATRIX.put(DocumentStatus.PENDING_APPROVAL, pendingApprovalTransitions);
        // 4. APPROVED (Đã duyệt: Văn thư cấp số, chờ Ký số PKI phát hành)
        Map<WorkflowAction, DocumentStatus> approvedTransitions = new EnumMap<>(WorkflowAction.class);
        approvedTransitions.put(WorkflowAction.SIGN_AND_PUBLISH, DocumentStatus.PUBLISHED); // Ký số PKI ban hành
        approvedTransitions.put(WorkflowAction.REQUEST_REVISION, DocumentStatus.NEEDS_REVISION);
        TRANSITION_MATRIX.put(DocumentStatus.APPROVED, approvedTransitions);
        // 5. NEEDS_REVISION (Yêu cầu chỉnh sửa)
        Map<WorkflowAction, DocumentStatus> needsRevisionTransitions = new EnumMap<>(WorkflowAction.class);
        needsRevisionTransitions.put(WorkflowAction.SUBMIT, DocumentStatus.IN_REVIEW); // Sửa xong gửi duyệt lại
        needsRevisionTransitions.put(WorkflowAction.WITHDRAW, DocumentStatus.DRAFT); // Rút về hẳn DRAFT
        TRANSITION_MATRIX.put(DocumentStatus.NEEDS_REVISION, needsRevisionTransitions);
        // 6. PUBLISHED (Đã ban hành: cho phép Thu hồi văn bản)
        Map<WorkflowAction, DocumentStatus> publishedTransitions = new EnumMap<>(WorkflowAction.class);
        publishedTransitions.put(WorkflowAction.REVOKE, DocumentStatus.REVOKED);
        TRANSITION_MATRIX.put(DocumentStatus.PUBLISHED, publishedTransitions);
        // 7. REJECTED (Bị từ chối: cho phép nạp file v2 gửi duyệt lại hoặc rút về DRAFT)
        Map<WorkflowAction, DocumentStatus> rejectedTransitions = new EnumMap<>(WorkflowAction.class);
        rejectedTransitions.put(WorkflowAction.SUBMIT, DocumentStatus.IN_REVIEW);
        rejectedTransitions.put(WorkflowAction.WITHDRAW, DocumentStatus.DRAFT);
        TRANSITION_MATRIX.put(DocumentStatus.REJECTED, rejectedTransitions);
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

        // 2. Yêu cầu bắt buộc lý do khi từ chối, yêu cầu sửa đổi hoặc thu hồi
        if ((action == WorkflowAction.REQUEST_REVISION || action == WorkflowAction.REJECT || action == WorkflowAction.REVOKE)
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

        if (!hasActionPermission(document, actor, action)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION);
        }
    }

    private boolean hasActionPermission(Document document, UserLogin actor, WorkflowAction action) {
        boolean isDrafter = document.getDrafter() != null && Objects.equals(document.getDrafter().getId(), actor.getId());
        boolean isAssignedSigner = document.getSigner() != null && Objects.equals(document.getSigner().getId(), actor.getId());
        Role role = actor.getRole();
        boolean isReviewerOrLeader = role == Role.REVIEWER || role == Role.LEADER || isAssignedSigner;

        return switch (action) {
            case SUBMIT, WITHDRAW -> isDrafter;
            case REVIEW_APPROVE, INITIAL_SIGN, REQUEST_REVISION, REJECT -> isReviewerOrLeader;
            case LEADER_APPROVE -> role == Role.LEADER || isAssignedSigner;
            case SIGN_AND_PUBLISH -> role == Role.CLERK || isAssignedSigner;
            case REVOKE -> isAssignedSigner || role == Role.LEADER;
        };
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
