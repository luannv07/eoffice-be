package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
public class SubmitApprovalRequest {

    @NotNull(message = "Người duyệt không được để trống")
    @Schema(description = "ID người duyệt được chỉ định", example = "1002")
    Long approverUserId;

    @Schema(description = "Ghi chú gửi người duyệt", example = "Kính gửi thầy Hiệu trưởng xem xét và phê duyệt bản dự thảo")
    String submitNote;
}
