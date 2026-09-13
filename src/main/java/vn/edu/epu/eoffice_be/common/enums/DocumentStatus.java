package vn.edu.epu.eoffice_be.common.enums;

import lombok.Getter;


@Getter

public enum DocumentStatus {
    DRAFT("Dự thảo / Bản nháp"),
    SUBMITTED("Đã gửi duyệt"),
    IN_REVIEW("Đang trong luồng duyệt / ký nháy"),
    LEADER_REVIEW("Chờ lãnh đạo xem xét"),
    PENDING_REVIEW("Chờ chuyên viên thẩm định"),
    PENDING_APPROVAL("Chờ phê duyệt"),
    PENDING_SIGN("Chờ ký số"),
    INITIAL_SIGNED("Đã ký nháy"),
    NEEDS_REVISION("Yêu cầu chỉnh sửa"),
    APPROVED("Đã phê duyệt"),
    PUBLISHED("Đã ban hành chính thức"),
    REJECTED("Bị từ chối phê duyệt");
    private final String description;
    DocumentStatus(String description) {
        this.description = description;
    }
}