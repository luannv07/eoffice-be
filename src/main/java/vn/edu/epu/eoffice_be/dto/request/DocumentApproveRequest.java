package vn.edu.epu.eoffice_be.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
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
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Yêu cầu phê duyệt văn bản (UC12)")
public class DocumentApproveRequest {

    @Size(max = 2000, message = "Ý kiến nhận xét tối đa 2000 ký tự")
    @Schema(description = "Ý kiến nhận xét hoặc ghi chú", example = "Đồng ý thông qua kế hoạch")
    private String comment;

    @Size(max = 2000, message = "Ý kiến chỉ đạo tối đa 2000 ký tự")
    @Schema(description = "Ý kiến chỉ đạo / Bút phê lãnh đạo (Leadership Endorsement)", example = "Giao Phòng Đào tạo chủ trì thực hiện trước ngày 30/10")
    private String leadershipInstruction;

    @Positive(message = "ID phòng ban / đơn vị phải là số dương")
    @Schema(description = "ID phòng ban / đơn vị được chỉ định chủ trì thực hiện", example = "1002")
    private Long assignedOrgNodeId;

    @Schema(description = "Số trang đặt chữ ký / con dấu phê duyệt (1-based, nếu để trống mặc định là trang cuối)", example = "1")
    private Integer signaturePage;

    @Schema(description = "Tọa độ X góc trái dưới khung chữ ký (PDF points, chuẩn A4 rộng 595pt)", example = "380.0")
    private Float signatureX;

    @Schema(description = "Tọa độ Y góc trái dưới khung chữ ký (PDF points, chuẩn A4 cao 842pt)", example = "120.0")
    private Float signatureY;

    @Schema(description = "Chiều rộng khung con dấu (PDF points, mặc định 150pt)", example = "150.0")
    private Float signatureWidth;

    @Schema(description = "Chiều cao khung con dấu (PDF points, mặc định 80pt)", example = "80.0")
    private Float signatureHeight;
}

