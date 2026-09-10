package vn.edu.epu.eoffice_be.common.enums;

import lombok.Getter;


@Getter

public enum DocumentStatus {
    DRAFT("Dự thảo / Bản nháp"),
    SUBMITTED("Đã gửi duyệt"),
    IN_REVIEW("Đang trong luồng duyệt / ký nháy"),
    LEADER_REVIEW("Chờ lãnh đạo phê duyệt"),
    NEEDS_REVISION("Yêu cầu chỉnh sửa"),
    APPROVED("Đã phê duyệt"),
    PUBLISHED("Đã ban hành chính thức"),
    REJECTED("Bị từ chối phê duyệt");
    private final String description;
    DocumentStatus(String description) {
        this.description = description;
    }
}