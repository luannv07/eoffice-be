package vn.edu.epu.eoffice_be.dto.request;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.epu.eoffice_be.common.enums.DistributionScope;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Văn thư ký niêm phong và phát hành văn bản đã cấp số, ký nháy (UC14)")
public class DocumentPublishRequest {

    @Schema(description = "Ngày ký ban hành chính thức (mặc định lấy thời điểm hiện tại)", example = "2026-09-10T08:00:00Z")
    private Instant issueDate;

    @Schema( description = "Ngày văn bản bắt đầu có hiệu lực. Quy tắc: Không được trước ngày ban hành (issueDate). Nếu để trống sẽ mặc định bằng ngày ban hành.",
        example = "2026-09-10T08:00:00Z")
    private Instant effectiveDate;
    

    @Size(max = 2000, message = "Lời phê hoặc ý kiến tối đa 2000 ký tự")
    @Schema(description = "Ghi chú của văn thư khi niêm phong và phát hành", example = "Niêm phong, ban hành và phân phối văn bản")
    private String comment;

    @Min(value = 1, message = "Số trang đặt chữ ký phải lớn hơn hoặc bằng 1")
    @Schema(description = "Số trang đặt chữ ký / con dấu phát hành (1-based, mặc định là trang cuối)", example = "1")
    private Integer signaturePage;

    @PositiveOrZero(message = "Tọa độ X phải lớn hơn hoặc bằng 0")
    @Schema(description = "Tọa độ X góc trái dưới khung chữ ký (PDF points, chuẩn A4 rộng 595pt)", example = "350.0")
    private Float signatureX;

    @PositiveOrZero(message = "Tọa độ Y phải lớn hơn hoặc bằng 0")
    @Schema(description = "Tọa độ Y góc trái dưới khung chữ ký (PDF points, chuẩn A4 cao 842pt)", example = "150.0")
    private Float signatureY;

    @Positive(message = "Chiều rộng khung con dấu phải là số dương")
    @Schema(description = "Chiều rộng khung con dấu (PDF points, mặc định 180pt)", example = "180.0")
    private Float signatureWidth;

    @Positive(message = "Chiều cao khung con dấu phải là số dương")
    @Schema(description = "Chiều cao khung con dấu (PDF points, mặc định 60pt)", example = "60.0")
    private Float signatureHeight;

    @NotNull(message = "Phải chọn phạm vi phát hành")
    @Schema(description = "Phạm vi phát hành: ALL, MULTIPLE_ORGS, SINGLE_ORG")
    private DistributionScope distributionScope;

    @Schema(description = "Danh sách ID khoa/phòng nhận văn bản")
    private List<@NotNull @Positive Long> recipientOrgIds;

    @jakarta.validation.constraints.AssertTrue(message = "Ngày có hiệu lực (effectiveDate) không được trước ngày ban hành (issueDate)")
    public boolean isEffectiveDateValid() {
        if (effectiveDate == null || issueDate == null) {
            return true;
        }
        return !effectiveDate.isBefore(issueDate);
    }
}
