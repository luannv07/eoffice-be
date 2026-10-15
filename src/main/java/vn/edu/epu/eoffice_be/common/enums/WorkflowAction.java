package vn.edu.epu.eoffice_be.common.enums;

import lombok.Getter;

@Getter
public enum WorkflowAction {
    SUBMIT("Gửi văn bản để duyệt"),
    REVIEW_APPROVE("Duyệt chuyển tiếp"),
    REQUEST_REVISION("Yêu cầu chỉnh sửa"),
    INITIAL_SIGN("Ký nháy văn bản"),
    LEADER_APPROVE("Lãnh đạo phê duyệt"),
    SIGN_AND_PUBLISH("Ký phát hành chính thức"),
    REJECT("Từ chối phê duyệt"),
    WITHDRAW("Thu hồi yêu cầu duyệt"),
    REVOKE("Thu hồi văn bản đã ban hành");
    private final String description;
    WorkflowAction(String description) {
        this.description = description;
    }
}