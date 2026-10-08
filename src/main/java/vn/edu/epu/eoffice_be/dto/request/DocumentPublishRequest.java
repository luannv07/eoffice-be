package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Yêu cầu phê duyệt và ký phát hành văn bản chính thức (UC14)")
public class DocumentPublishRequest {

    @NotNull(message = "Bắt buộc chọn sổ văn bản để cấp số")
    @Schema(description = "ID sổ văn bản dùng để cấp số", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    private Long bookId;

    @Schema(description = "Số hiệu văn bản chính thức (tự động cấp từ sổ văn bản qua Pessimistic Lock, không cần nhập)", example = "125/QĐ-ĐHEPU")
    private String officialNumber;

    @Schema(description = "Ngày ký ban hành chính thức (mặc định lấy thời điểm hiện tại)", example = "2026-09-10T08:00:00Z")
    private Instant issueDate;

    @Schema( description = "Ngày văn bản bắt đầu có hiệu lực. Quy tắc: Không được trước ngày ban hành (issueDate). Nếu để trống sẽ mặc định bằng ngày ban hành.",
        example = "2026-09-10T08:00:00Z")
    private Instant effectiveDate;
    

    @Size(max = 2000, message = "Lời phê hoặc ý kiến tối đa 2000 ký tự")
    @Schema(description = "Lời phê hoặc ý kiến của Lãnh đạo ký phát hành", example = "Ký duyệt ban hành và chuyển văn thư đóng dấu lưu trữ")
    private String comment;

    @jakarta.validation.constraints.AssertTrue(message = "Ngày có hiệu lực (effectiveDate) không được trước ngày ban hành (issueDate)")
    public boolean isEffectiveDateValid() {
        if (effectiveDate == null || issueDate == null) {
            return true;
        }
        return !effectiveDate.isBefore(issueDate);
    }
}
