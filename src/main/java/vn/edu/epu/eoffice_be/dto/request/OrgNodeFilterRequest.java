package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Bộ lọc cây tổ chức (dạng query param). Lưu ý: page/size không áp dụng — endpoint trả về toàn bộ cây")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrgNodeFilterRequest extends BaseFilterRequest {

	@Schema(description = "Lọc theo mã đơn vị (contains, không phân biệt hoa thường)", example = "CNTT")
	String code;

	@Schema(description = "Lọc theo tên tiếng Việt (contains, không phân biệt hoa thường)", example = "công nghệ")
	String nameVn;

	@Schema(description = "Lọc theo loại đơn vị (khớp chính xác). Giá trị seed hiện có: UNIVERSITY / FACULTY / DEPARTMENT / CENTER / MAJOR",
					example = "FACULTY")
	String typeCd;
}
