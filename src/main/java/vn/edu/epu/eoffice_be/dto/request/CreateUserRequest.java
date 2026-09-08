package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.enums.Role;

@Getter
@Setter
@Schema(description = "Request tạo tài khoản user mới bởi admin")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateUserRequest {

    @Schema(description = "Tên đăng nhập (duy nhất)", example = "nguyen.van.a", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "username không được để trống")
    @Size(max = 100, message = "username tối đa 100 ký tự")
    String username;

    @Schema(description = "Email liên hệ (duy nhất)", example = "nguyen.van.a@epu.edu.vn", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "email không được để trống")
    @Email(message = "email không hợp lệ")
    @Size(max = 255, message = "email tối đa 255 ký tự")
    String email;

    @Schema(description = "Mật khẩu tạm, tối thiểu 8 / tối đa 72 ký tự (bcrypt)", example = "MatKhau@2026", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "password không được để trống")
    @Size(min = 8, message = "password tối thiểu 8 ký tự")
    @Size(max = 72, message = "password tối đa 72 ký tự")
    String password;

    @Schema(description = "Vai trò gán cho user; mặc định USER. Chỉ SYSTEM_ADMIN được tạo tài khoản SYSTEM_ADMIN.", example = "USER")
    Role role = Role.USER;

    @Schema(description = "Yêu cầu user đổi mật khẩu lần đầu đăng nhập", example = "true")
    boolean mustChangePassword = true;
}
