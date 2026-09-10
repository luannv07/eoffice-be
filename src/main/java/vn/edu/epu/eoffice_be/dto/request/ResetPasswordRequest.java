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
@Schema(description = "Request đặt lại mật khẩu bằng link quên mật khẩu")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ResetPasswordRequest {

	@Schema(description = "Token raw lấy từ link reset password", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "token không được để trống")
	String token;

	@Schema(description = "Mật khẩu mới, tối thiểu 8 ký tự, tối đa 72 ký tự", example = "NewPassword@2026", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "newPassword không được để trống")
	@Size(min = 8, message = "newPassword tối thiểu 8 ký tự")
	@Size(max = 72, message = "newPassword tối đa 72 ký tự")
	String newPassword;
}
