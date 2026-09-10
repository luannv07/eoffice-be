package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Schema(description = "Request cập nhật mục từ điển. Không chứa groupCd/itemCd — hai mã này bất khả biến sau khi tạo "
				+ "vì được các nghiệp vụ khác tham chiếu theo mã")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DictionaryItemUpdateRequest {

	@Schema(description = "Tên hiển thị tiếng Việt", example = "Bị khoá tạm thời", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "nameVn không được để trống")
	@Size(max = 255, message = "nameVn tối đa 255 ký tự")
	String nameVn;

	@Schema(description = "Tên hiển thị tiếng Anh. Bỏ trống để giữ nguyên giá trị cũ", example = "Temporarily locked")
	@Size(max = 255, message = "nameEn tối đa 255 ký tự")
	String nameEn;

	@Schema(description = "Thứ tự sắp xếp khi hiển thị. Bỏ trống để giữ nguyên giá trị cũ", example = "1")
	Integer displayOrder;

	@Schema(description = "Mục có được dùng hay không. Bỏ trống để giữ nguyên giá trị cũ", example = "false")
	Boolean activeFlg;
}
