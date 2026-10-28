package vn.edu.epu.eoffice_be.dto.response;

import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.SubmissionRoute;
import java.util.List;

public record SubmissionOptionsResponse(SelectionMode selectionMode, List<RouteOption> routes) {
    public enum SelectionMode { SELECT, AUTO, QUEUE }

    public record RouteOption(SubmissionRoute route, DocumentStatus nextStatus,
                              List<EligibleApproverResponse> approvers) {}
}
