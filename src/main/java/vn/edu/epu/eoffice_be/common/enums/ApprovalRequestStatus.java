package vn.edu.epu.eoffice_be.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApprovalRequestStatus {
    PENDING("Đang chờ duyệt"),
    APPROVED("Đã duyệt"),
    REJECTED("Từ chối / Yêu cầu chỉnh sửa"),
    RECALLED("Đã thu hồi");

    private final String description;
}
