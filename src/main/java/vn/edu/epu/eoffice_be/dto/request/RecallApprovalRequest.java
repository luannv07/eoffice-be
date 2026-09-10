package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RecallApprovalRequest {

    @Schema(description = "Lý do thu hồi yêu cầu duyệt", example = "Cần chỉnh sửa bổ sung thông tin số liệu trước khi trình duyệt lại")
    String recallReason;
}
