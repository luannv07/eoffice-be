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
@Schema(description = "Yêu cầu thu hồi văn bản đã ban hành chính thức")
public class DocumentRevokeRequest {

    @NotBlank(message = "Lý do thu hồi văn bản không được để trống")
    @Schema(description = "Lý do thu hồi văn bản đã ban hành", requiredMode = Schema.RequiredMode.REQUIRED, example = "Phát hiện sai sót số liệu tại Phụ lục 2; ban hành văn bản thay thế số 45/QD-DHEPU")
    private String comment;
}
