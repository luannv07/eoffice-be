package vn.edu.epu.eoffice_be.common.enums;

import vn.edu.epu.eoffice_be.common.exception.BusinessException;

/** The backend selects the signing stage; clients cannot choose it. */
public enum InitialSignStage {
    DRAFTER, REVIEWER, LEADER, CLERK;

    public static InitialSignStage fromStatus(DocumentStatus status) {
        if (status == null) throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE);
        return switch (status) {
            case DRAFT, NEEDS_REVISION, REJECTED -> DRAFTER;
            case IN_REVIEW -> REVIEWER;
            case PENDING_APPROVAL -> LEADER;
            case APPROVED -> CLERK;
            default -> throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE);
        };
    }

    public String fieldName(String userId) {
        return "sig_user_" + userId + "_" + name();
    }
}
