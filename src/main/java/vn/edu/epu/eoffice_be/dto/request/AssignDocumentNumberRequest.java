package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@Schema(description = "Request cấp số văn bản (UC07)")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssignDocumentNumberRequest {

	@Schema(description = "ID sổ văn bản cần cấp số", example = "1001", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull(message = "bookId không được để trống")
	Long bookId;

	@Schema(description = "Ngày có hiệu lực của văn bản", example = "2026-09-09", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull(message = "effectiveDate không được để trống")
	LocalDate effectiveDate;

	@Schema(description = "ID văn bản cần cấp số (tùy chọn khi chưa tích hợp module Document)", example = "2001")
	Long documentId;
}
