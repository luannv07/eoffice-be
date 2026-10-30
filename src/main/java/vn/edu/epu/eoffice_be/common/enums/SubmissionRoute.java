package vn.edu.epu.eoffice_be.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubmissionRoute {
    REVIEW(DocumentStatus.IN_REVIEW),
    LEADER_APPROVAL(DocumentStatus.PENDING_APPROVAL),
    CLERK_QUEUE(DocumentStatus.APPROVED);

    private final DocumentStatus nextStatus;
}
