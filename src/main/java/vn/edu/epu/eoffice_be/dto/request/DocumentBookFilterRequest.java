package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Request lọc và phân trang danh sách sổ văn bản")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentBookFilterRequest extends BaseFilterRequest {

	@Schema(description = "Lọc theo năm sổ văn bản", example = "2026")
	Integer year;

	@Schema(description = "Lọc theo cấp sổ: 1 - Trường/Đơn vị, 2 - Phòng/Ban", example = "1")
	Integer bookLevel;

	@Schema(description = "Lọc theo đơn vị/phòng ban sở hữu sổ", example = "1")
	Long orgNodeId;

	@Schema(description = "Lọc theo loại văn bản (ID dictionary item)", example = "3001")
	Long documentTypeId;

	@Schema(description = "Lọc theo trạng thái đóng sổ: true (đã đóng), false (đang mở)", example = "false")
	Boolean isClosed;

	@Schema(description = "Từ khóa tìm kiếm theo mã sổ hoặc tên sổ", example = "Quyết định")
	String keyword;

	@Override
	protected Set<String> getAllowedSortFields() {
		return Set.of("createdDate", "updatedDate", "year", "bookCode", "bookName", "currentNumber");
	}

	@Override
	protected String getDefaultSortBy() {
		return "year";
	}
}
