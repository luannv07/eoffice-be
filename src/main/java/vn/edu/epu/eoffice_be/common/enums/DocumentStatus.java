package vn.edu.epu.eoffice_be.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Trạng thái vòng đời của văn bản trong hệ thống e-Office.
 * <p>
 * Lưu ý quan trọng:
 * 1. Thuộc tính {@code description} là một phần của API public contract (trả về qua field {@code statusDescription} trong DTO).
 *    Các mô tả như "Đã phê duyệt" (APPROVED) và "Đã ban hành chính thức" (PUBLISHED) cần được bảo toàn ổn định.
 * 2. Mọi entity JPA lưu trữ enum này đều cấu hình {@code @Enumerated(EnumType.STRING)}, do đó không phụ thuộc vào ordinal.
 *    Tuyệt đối không sử dụng {@code ordinal()} để lưu trữ hoặc tuần tự hóa dữ liệu.
 */
@Getter
@RequiredArgsConstructor
public enum DocumentStatus {
    DRAFT("Dự thảo / Bản nháp"),
    IN_REVIEW("Đang thẩm định & ký nháy"),
    PENDING_APPROVAL("Chờ lãnh đạo phê duyệt"),
    APPROVED("Đã phê duyệt"),
    PUBLISHED("Đã ban hành chính thức"),
    NEEDS_REVISION("Yêu cầu chỉnh sửa"),
    REJECTED("Bị từ chối phê duyệt"),
    REVOKED("Đã thu hồi");

    private final String description;
}