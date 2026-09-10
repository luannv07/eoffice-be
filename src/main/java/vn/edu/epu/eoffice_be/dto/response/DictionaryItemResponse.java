package vn.edu.epu.eoffice_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Schema(description = "Mục từ điển dữ liệu dùng chung của hệ thống")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryItemResponse {

	@Schema(description = "Id bản ghi", example = "1002")
	Long id;

	@Schema(description = "Mã nhóm từ điển", example = "USER_STATUS_CD")
	String groupCd;

	@Schema(description = "Mã mục (duy nhất trong nhóm)", example = "LOCKED")
	String itemCd;

	@Schema(description = "Tên hiển thị tiếng Việt", example = "Bị khoá tạm")
	String nameVn;

	@Schema(description = "Tên hiển thị tiếng Anh", example = "Locked")
	String nameEn;

	@Schema(description = "Thứ tự sắp xếp khi hiển thị", example = "1")
	Integer displayOrder;

	@Schema(description = "Mục có được dùng hay không", example = "true")
	Boolean activeFlg;

	@Schema(description = "Thời điểm tạo", example = "2026-08-10T03:00:00Z")
	Instant createdDate;

	@Schema(description = "Thời điểm cập nhật gần nhất", example = "2026-08-20T08:30:15Z")
	Instant updatedDate;
}
