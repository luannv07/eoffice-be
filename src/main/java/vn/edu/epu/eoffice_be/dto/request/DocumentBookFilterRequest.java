package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

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
	public Pageable pageable() {
		Pageable base = super.pageable();
		Sort sort = base.getSort();
		if (sort.getOrderFor("id") == null) {
			sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
		}
		return PageRequest.of(base.getPageNumber(), base.getPageSize(), sort);
	}

	@Override
	protected Set<String> getAllowedSortFields() {
		return Set.of("createdDate", "updatedDate", "year", "bookCode", "bookName", "currentNumber", "id");
	}

	@Override
	protected String getDefaultSortBy() {
		return "year";
	}
}
