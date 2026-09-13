package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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

    @NotBlank(message = "Số hiệu văn bản chính thức không được để trống")
    @Schema(description = "Số hiệu văn bản chính thức ban hành", requiredMode = Schema.RequiredMode.REQUIRED, example = "125/QĐ-ĐHEPU")
    private String officialNumber;

    @Schema(description = "Ngày ký ban hành chính thức (mặc định lấy thời điểm hiện tại)", example = "2026-09-10T08:00:00Z")
    private Instant issueDate;

    @Schema(description = "Ngày văn bản bắt đầu có hiệu lực (mặc định bằng ngày ban hành)", example = "2026-09-10T08:00:00Z")
    private Instant effectiveDate;

    @Size(max = 2000, message = "Lời phê hoặc ý kiến tối đa 2000 ký tự")
    @Schema(description = "Lời phê hoặc ý kiến của Lãnh đạo ký phát hành", example = "Ký duyệt ban hành và chuyển văn thư đóng dấu lưu trữ")
    private String comment;
}
