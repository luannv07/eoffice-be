package vn.edu.epu.eoffice_be.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DocumentProcessAction {
    CREATE_DRAFT("Tạo dự thảo"),
    UPDATE_DRAFT("Cập nhật dự thảo"),
    UPLOAD_ATTACHMENT("Tải lên tệp đính kèm"),
    DELETE_ATTACHMENT("Xóa tệp đính kèm"),
    RESTORE_VERSION("Khôi phục phiên bản"),
    SUBMIT_APPROVAL("Gửi duyệt"),
    RECALL_APPROVAL("Thu hồi yêu cầu duyệt");

    private final String description;
}
