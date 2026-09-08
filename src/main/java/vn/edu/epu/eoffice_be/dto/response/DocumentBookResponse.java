package vn.edu.epu.eoffice_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết sổ văn bản")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentBookResponse {

	@Schema(description = "ID sổ văn bản", example = "1001")
	Long id;

	@Schema(description = "Mã sổ văn bản", example = "SVD-QD-DHEPU-2026")
	String bookCode;

	@Schema(description = "Tên sổ văn bản", example = "Sổ Quyết định cấp Trường năm 2026")
	String bookName;

	@Schema(description = "Năm của sổ văn bản", example = "2026")
	Integer year;

	@Schema(description = "Cấp sổ: 1 - Cấp Đơn vị/Trường, 2 - Cấp Phòng/Ban", example = "1")
	Integer bookLevel;

	@Schema(description = "ID loại văn bản", example = "3001")
	Long documentTypeId;

	@Schema(description = "Tên loại văn bản", example = "Quyết định")
	String documentTypeName;

	@Schema(description = "Mã loại văn bản", example = "QD")
	String documentTypeCd;

	@Schema(description = "ID đơn vị/phòng ban sở hữu sổ", example = "1")
	Long orgNodeId;

	@Schema(description = "Tên đơn vị/phòng ban sở hữu sổ", example = "Trường Đại học Điện Lực")
	String orgNodeName;

	@Schema(description = "Mã đơn vị sở hữu sổ", example = "DHEPU")
	String orgNodeCode;

	@Schema(description = "Số văn bản hiện tại đã cấp trong sổ", example = "12")
	Integer currentNumber;

	@Schema(description = "Mẫu sinh số văn bản", example = "{NUM}/QĐ-ĐHEPU")
	String numberPattern;

	@Schema(description = "Trạng thái đóng sổ", example = "false")
	Boolean isClosed;

	@Schema(description = "Thời điểm tạo")
	Instant createdDate;

	@Schema(description = "Thời điểm cập nhật")
	Instant updatedDate;

	@Schema(description = "Người tạo")
	String createdBy;

	@Schema(description = "Người cập nhật")
	String updatedBy;
}
