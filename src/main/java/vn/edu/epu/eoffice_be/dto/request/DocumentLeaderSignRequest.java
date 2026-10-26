package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Ký hình ảnh phê duyệt, giữ nguyên trạng thái PENDING_APPROVAL")
public class DocumentLeaderSignRequest {
    @Size(max = 2000)
    private String comment;

    @Positive(message = "Số trang đặt chữ ký phải là số nguyên dương")
    @Schema(description = "Số trang đặt chữ ký / con dấu phê duyệt (1-based, nếu để trống mặc định là trang cuối)", example = "1")
    private Integer signaturePage;

    @Positive(message = "Tọa độ X phải là số dương")
    @Schema(description = "Tọa độ X góc trái dưới khung chữ ký (PDF points, chuẩn A4 rộng 595pt)", example = "380.0")
    private Float signatureX;

    @Positive(message = "Tọa độ Y phải là số dương")
    @Schema(description = "Tọa độ Y góc trái dưới khung chữ ký (PDF points, chuẩn A4 cao 842pt)", example = "120.0")
    private Float signatureY;

    @Positive(message = "Chiều rộng khung con dấu phải là số dương")
    @Schema(description = "Chiều rộng khung con dấu (PDF points, mặc định 150pt)", example = "150.0")
    private Float signatureWidth;

    @Positive(message = "Chiều cao khung con dấu phải là số dương")
    @Schema(description = "Chiều cao khung con dấu (PDF points, mặc định 80pt)", example = "80.0")
    private Float signatureHeight;
}
