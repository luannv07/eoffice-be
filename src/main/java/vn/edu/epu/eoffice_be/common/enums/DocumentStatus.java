package vn.edu.epu.eoffice_be.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DocumentStatus {
    DRAFT("Dự thảo"),
    WAITING_APPROVAL("Chờ duyệt"),
    APPROVED("Đã duyệt"),
    REJECTED("Bị từ chối / Cần sửa"),
    ISSUED("Đã phát hành"),
    REVOKED("Đã thu hồi");

    private final String description;
}
