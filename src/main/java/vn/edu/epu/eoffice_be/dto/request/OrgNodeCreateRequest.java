package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Schema(description = "Request tạo node tổ chức mới trong cây đơn vị")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrgNodeCreateRequest {

	@Schema(description = "Mã đơn vị (duy nhất trong hệ thống)", example = "CNTT", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank
	@Size(max = 50)
	String code;

	@Schema(description = "Id của node cha. Bỏ trống/null nếu tạo node gốc (ví dụ: Trường)",
			example = "1001", nullable = true)
	Long parentId;

	@Schema(description = "Tên tiếng Việt của đơn vị", example = "Khoa Công nghệ thông tin", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank
	@Size(max = 255)
	String nameVn;

	@Schema(description = "Tên tiếng Anh của đơn vị", example = "Faculty of Information Technology", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank
	@Size(max = 255)
	String nameEn;

	@Schema(description = "Loại đơn vị, phải tồn tại trong dictionary nhóm ORG_NODE_TYPE_CD. Giá trị seed hiện có: UNIVERSITY / FACULTY / DEPARTMENT / CENTER / MAJOR",
			example = "FACULTY", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank
	@Size(max = 50)
	String typeCd;

	@Schema(description = "Thứ tự sắp xếp trong các node cùng cấp", example = "2")
	Integer orgOrder;
}
