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
    SUBMITTED("Đã gửi duyệt"),
    IN_REVIEW("Đang trong luồng duyệt / ký nháy"),
    LEADER_REVIEW("Chờ lãnh đạo xem xét"),
    PENDING_REVIEW("Chờ chuyên viên thẩm định"),
    PENDING_APPROVAL("Chờ phê duyệt"),
    WAITING_APPROVAL("Chờ duyệt"),
    PENDING_SIGN("Chờ ký số"),
    INITIAL_SIGNED("Đã ký nháy"),
    NEEDS_REVISION("Yêu cầu chỉnh sửa"),
    APPROVED("Chờ ban hành"),
    ISSUED("Đã phát hành"),
    PUBLISHED("Đã ban hành chính thức"),
    REVOKED("Đã thu hồi"),
    REJECTED("Bị từ chối phê duyệt");

    private final String description;
}