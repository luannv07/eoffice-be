package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Schema(description = "Request đăng nhập hệ thống")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LoginRequest {

	@Schema(description = "Tên đăng nhập của tài khoản", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "username không được để trống")
	@Size(max = 100, message = "username tối đa 100 ký tự")
	String username;

	@Schema(description = "Mật khẩu dạng plain text (sẽ được mã hóa phía server)", example = "Admin@1234", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "password không được để trống")
	@Size(max = 255, message = "password tối đa 255 ký tự")
	String password;
}
