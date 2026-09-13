package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Yêu cầu từ chối phê duyệt văn bản (UC14)")
public class DocumentRejectRequest {

    @NotBlank(message = "Lý do từ chối phê duyệt không được để trống")
    @Schema(description = "Lý do từ chối phê duyệt văn bản", requiredMode = Schema.RequiredMode.REQUIRED, example = "Chủ trương tổ chức chưa phù hợp với kế hoạch năm học của Nhà trường")
    private String comment;
}
