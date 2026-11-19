package vn.edu.epu.eoffice_be.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Trang thai cua yeu cau phe duyet trong quy trinh
@Getter
@RequiredArgsConstructor
public enum ApprovalRequestStatus {
    PENDING("Đang chờ duyệt"),
    APPROVED("Đã duyệt"),
    REJECTED("Từ chối"),
    NEEDS_REVISION("Yêu cầu chỉnh sửa"),
    RECALLED("Đã thu hồi");

    private final String description;
}
