package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@Schema(description = "Yêu cầu chỉnh sửa / Trả về văn bản (UC12)")
public class DocumentRevisionRequest {

    @NotBlank(message = "Lý do và nội dung yêu cầu chỉnh sửa không được để trống")
    @Size(max = 2000, message = "Lý do và nội dung yêu cầu chỉnh sửa tối đa 2000 ký tự")
    @Schema(description = "Nội dung yêu cầu chỉnh sửa chi tiết cho người soạn thảo", requiredMode = Schema.RequiredMode.REQUIRED, example = "Cần bổ sung thêm dự toán kinh phí tại Mục 3 và đính kèm danh sách phân công")
    private String comment;
}
