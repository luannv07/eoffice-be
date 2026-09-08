package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Schema(description = "Request tạo sổ văn bản mới")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentBookCreateRequest {

	@Schema(description = "Mã sổ văn bản (duy nhất)", example = "SVD-QD-DHEPU-2026", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "bookCode không được để trống")
	@Size(max = 50, message = "bookCode tối đa 50 ký tự")
	String bookCode;

	@Schema(description = "Tên sổ văn bản", example = "Sổ Quyết định cấp Trường năm 2026", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "bookName không được để trống")
	@Size(max = 255, message = "bookName tối đa 255 ký tự")
	String bookName;

	@Schema(description = "Năm của sổ văn bản", example = "2026", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull(message = "year không được để trống")
	@Min(value = 2000, message = "Năm phải từ 2000 trở lên")
	@Max(value = 2100, message = "Năm tối đa là 2100")
	Integer year;

	@Schema(description = "Cấp sổ: 1 - Cấp Đơn vị/Trường, 2 - Cấp Phòng/Ban", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull(message = "bookLevel không được để trống")
	@Min(value = 1, message = "bookLevel chỉ nhận 1 (Đơn vị/Trường) hoặc 2 (Phòng/Ban)")
	@Max(value = 2, message = "bookLevel chỉ nhận 1 (Đơn vị/Trường) hoặc 2 (Phòng/Ban)")
	Integer bookLevel;

	@Schema(description = "ID loại văn bản (FK tới dictionary_items)", example = "3001", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull(message = "documentTypeId không được để trống")
	Long documentTypeId;

	@Schema(description = "ID đơn vị/phòng ban sở hữu sổ (FK tới org_nodes)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull(message = "orgNodeId không được để trống")
	Long orgNodeId;

	@Schema(description = "Mẫu sinh số văn bản. Bắt buộc chứa {NUM}/[MÃ_LOẠI]-[MÃ_ĐƠN_VỊ]", example = "{NUM}/QĐ-ĐHEPU", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "numberPattern không được để trống")
	@Pattern(
			regexp = "^\\{NUM\\}\\/[A-Za-z0-9À-ỹĐđ]+(?:[-/.][A-Za-z0-9À-ỹĐđ]+)+$",
			message = "Mẫu số văn bản phải có định dạng {NUM}/[MÃ_LOẠI]-[MÃ_ĐƠN_VỊ] (ví dụ: {NUM}/QĐ-ĐHEPU hoặc {NUM}/TB-CNTT)"
	)
	String numberPattern;
}
