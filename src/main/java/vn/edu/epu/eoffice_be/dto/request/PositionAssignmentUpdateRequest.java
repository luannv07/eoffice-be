package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Schema(description = "Request cập nhật quyết định bổ nhiệm (miễn nhiệm hoặc kéo dài thời hạn)")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PositionAssignmentUpdateRequest {

	@Schema(description = "Thời điểm kết thúc hiệu lực mới (ISO-8601)", example = "2027-12-31T23:59:59Z")
	Instant effectiveTo;

	@Schema(description = "Ngày miễn nhiệm thực tế (ISO-8601). Set để kết thúc bổ nhiệm sớm hơn effectiveTo", example = "2026-06-30T00:00:00Z")
	Instant actualDismissalDate;

	@Schema(description = "Mã lý do miễn nhiệm", example = "TRANSFER", nullable = true)
	@Size(max = 50, message = "dismissalReasonCd tối đa 50 ký tự")
	String dismissalReasonCd;

	@Schema(description = "Ghi chú khi miễn nhiệm", example = "Chuyển sang đơn vị khác", nullable = true)
	String dismissalNote;
}
