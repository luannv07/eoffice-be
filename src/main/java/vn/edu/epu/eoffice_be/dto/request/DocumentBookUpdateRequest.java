package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Schema(description = "Request cập nhật sổ văn bản")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentBookUpdateRequest {

	@Schema(description = "Tên sổ văn bản mới", example = "Sổ Quyết định cấp Trường năm 2026 (Cập nhật)")
	@Size(max = 255, message = "bookName tối đa 255 ký tự")
	String bookName;

	@Schema(description = "Mẫu sinh số văn bản mới", example = "{NUM}/QĐ-ĐHEPU")
	@Pattern(
			regexp = "^\\{NUM\\}\\/[A-Za-z0-9À-ỹĐđ]+(?:[-/.][A-Za-z0-9À-ỹĐđ]+)+$",
			message = "Mẫu số văn bản phải có định dạng {NUM}/[MÃ_LOẠI]-[MÃ_ĐƠN_VỊ] (ví dụ: {NUM}/QĐ-ĐHEPU hoặc {NUM}/TB-CNTT)"
	)
	String numberPattern;
}
