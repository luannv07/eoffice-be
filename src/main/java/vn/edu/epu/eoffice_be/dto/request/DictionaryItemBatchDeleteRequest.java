package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Schema(description = "Request xóa mềm nhiều mục từ điển trong một transaction")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DictionaryItemBatchDeleteRequest {

	@Schema(description = "Danh sách id các mục từ điển cần xóa. Chỉ id tồn tại và chưa xóa mềm mới được tính vào kết quả",
					example = "[1051, 1052, 1053]", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotEmpty(message = "ids không được để trống")
	List<Long> ids;
}
