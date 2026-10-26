package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Ký nháy riêng theo giai đoạn hiện tại, không chuyển trạng thái (UC13)")
public class DocumentInitialSignRequest {

    @Schema(description = "Ý kiến hoặc ghi chú khi ký nháy", example = "Đã rà soát thể thức văn bản và nội dung chuyên môn, kính trình Lãnh đạo")
    private String comment;
}
