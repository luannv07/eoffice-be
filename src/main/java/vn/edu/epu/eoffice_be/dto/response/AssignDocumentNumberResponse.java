package vn.edu.epu.eoffice_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả cấp số văn bản (UC07)")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssignDocumentNumberResponse {

	@Schema(description = "Số hiệu văn bản được cấp chính thức", example = "1/QĐ-ĐHEPU")
	String documentNumber;

	@Schema(description = "ID sổ văn bản đã cấp số", example = "1001")
	Long bookId;

	@Schema(description = "Tên sổ văn bản", example = "Sổ Quyết định cấp Trường năm 2026")
	String bookName;

	@Schema(description = "Số thứ tự vừa cấp trong sổ", example = "1")
	Integer currentNumber;

	@Schema(description = "Ngày có hiệu lực của văn bản", example = "2026-09-09")
	LocalDate effectiveDate;

	@Schema(description = "ID văn bản được cấp số (nếu có)", example = "2001")
	Long documentId;

}
