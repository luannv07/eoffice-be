package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Schema(description = "Request đăng ký tài khoản mới (mặc định role USER, chưa có hồ sơ)")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterRequest {

	@Schema(description = "Tên đăng nhập (duy nhất trong hệ thống)", example = "nguyen.van.a", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "username không được để trống")
	@Size(max = 100, message = "username tối đa 100 ký tự")
	String username;

	@Schema(description = "Email liên hệ (duy nhất trong hệ thống)", example = "nguyen.van.a@epu.edu.vn", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "email không được để trống")
	@Email(message = "email không hợp lệ")
	@Size(max = 255, message = "email tối đa 255 ký tự")
	String email;

	@Schema(description = "Mật khẩu, tối thiểu 8 ký tự, tối đa 72 ký tự (giới hạn bcrypt)", example = "MatKhau@2026", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "password không được để trống")
	@Size(min = 8, message = "password tối thiểu 8 ký tự")
	@Size(max = 72, message = "password tối đa 72 ký tự")
	String password;
}
