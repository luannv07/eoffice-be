package vn.edu.epu.eoffice_be.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.ApprovalRequestStatus;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DocumentApprovalRequestRepository;

import java.util.Set;

/** Current assignment permits processing; historical participation permits reading only. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewAssignmentPolicy {
    private static final Set<WorkflowAction> ACTIONS = Set.of(WorkflowAction.INITIAL_SIGN,
            WorkflowAction.REVIEW_APPROVE, WorkflowAction.REQUEST_REVISION, WorkflowAction.REJECT);
    private final DocumentReadPolicy readers;
    private final DocumentApprovalRequestRepository requests;

    public static boolean applies(Document document, WorkflowAction action) {
        return document != null && document.getStatus() == DocumentStatus.IN_REVIEW && ACTIONS.contains(action);
    }

    public UserLogin requireAssignedActor(Document document, UserLogin actor) {
        UserLogin verified = readers.requireUser(actor == null ? null : actor.getId());
        DocumentAdminPolicy.assertBusinessActor(verified);
        if (document == null || document.getId() == null) {
            throw invalidRequest();
        }
        // Include deleted requests in ordering: deleting the latest must not revive an old assignment.
        var request = requests.findFirstByDocumentIdOrderBySubmittedAtDescIdDesc(document.getId())
                .orElseThrow(ReviewAssignmentPolicy::invalidRequest);
        if (request.isDeleteFlg() || request.getStatus() != ApprovalRequestStatus.PENDING
                || request.getSubmittedAt() == null || request.getApproverUser() == null
                || request.getApproverUser().getId() == null) {
            throw invalidRequest();
        }
        if (!verified.getId().equals(request.getApproverUser().getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Văn bản không được giao cho bạn xử lý.");
        }
        return verified;
    }

    private static BusinessException invalidRequest() {
        return new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE,
                "Không xác định được yêu cầu thẩm định hiện hành hợp lệ. Vui lòng kiểm tra luồng xử lý.");
    }
}
