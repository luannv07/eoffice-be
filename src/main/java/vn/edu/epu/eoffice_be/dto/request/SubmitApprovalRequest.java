package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import vn.edu.epu.eoffice_be.common.enums.SubmissionRoute;
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
public class SubmitApprovalRequest {

    @Positive(message = "ID người nhận phải lớn hơn 0")
    @Schema(description = "ID người nhận; bắt buộc với tuyến lựa chọn, bỏ trống với hàng chờ văn thư", example = "1002")
    Long approverUserId;

    @Schema(description = "Tuyến gửi: REVIEW, LEADER_APPROVAL, CLERK_QUEUE; admin phải chọn tuyến")
    SubmissionRoute route;

    @Size(max = 1000, message = "Ghi chú gửi người duyệt không được vượt quá 1000 ký tự")
    @Schema(description = "Ghi chú gửi người duyệt", example = "Kính gửi thầy Hiệu trưởng xem xét và phê duyệt bản dự thảo")
    String submitNote;
}
