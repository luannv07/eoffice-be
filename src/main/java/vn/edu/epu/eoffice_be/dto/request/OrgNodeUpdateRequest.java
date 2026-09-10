package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Schema(description = "Request cập nhật node tổ chức: đổi tên và/hoặc di chuyển sang node cha khác")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrgNodeUpdateRequest {

	@Schema(description = "Id của node cần cập nhật (được truyền qua path, controller tự gán; không nằm trong body)", example = "1015", nullable = true)
	Long id;

	// nullable: null = chuyển thành root; khác null = đổi cha
	@Schema(description = "Id node cha mới. null = chuyển thành node gốc; khác null = di chuyển sang cha mới",
					example = "1001", nullable = true)
	Long parentId;

	@Schema(description = "Tên tiếng Việt mới", example = "Khoa Công nghệ thông tin và Truyền thông", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank
	@Size(max = 255)
	String nameVn;

	@Schema(description = "Tên tiếng Anh mới", example = "Faculty of Information and Communication Technology", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank
	@Size(max = 255)
	String nameEn;

	@Schema(description = "Thứ tự sắp xếp mới trong các node cùng cấp. Bỏ trống/null để giữ nguyên thứ tự hiện tại (dùng cho kéo thả đổi vị trí anh em)", example = "3", nullable = true)
	Integer orgOrder;
}
