package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @NotNull(message = "Lý do thu hồi không được để trống")
    @Size(max = 500, message = "Lý do thu hồi không được vượt quá 500 ký tự")
    @Schema(description = "Lý do thu hồi yêu cầu duyệt", example = "Cần chỉnh sửa bổ sung thông tin số liệu trước khi trình duyệt lại")
    String recallReason;
}
