package vn.edu.epu.eoffice_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.enums.Role;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Tóm tắt user trong danh sách quản lý")
public class UserSummaryResponse {

	@Schema(description = "ID user")
	Long id;

	@Schema(description = "Tên đăng nhập")
	String username;

	@Schema(description = "Email liên hệ")
	String email;

	@Schema(description = "Vai trò")
	Role role;

	@Schema(description = "Trạng thái: ACTIVE / LOCKED / DISABLED")
	String userStatusCd;

	@Schema(description = "Bắt buộc đổi mật khẩu lần sau")
	boolean mustChangePassword;

	@Schema(description = "Thời điểm tạo")
	Instant createdDate;

	@Schema(description = "Thời điểm cập nhật gần nhất")
	Instant updatedDate;
}
