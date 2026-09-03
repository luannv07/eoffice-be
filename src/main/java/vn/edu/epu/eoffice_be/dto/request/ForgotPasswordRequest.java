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
@Schema(description = "Request yêu cầu gửi link đặt lại mật khẩu")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ForgotPasswordRequest {

    @Schema(description = "Email đăng nhập đã đăng ký trong hệ thống", example = "nguyen.van.a@epu.edu.vn", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "email không được để trống")
    @Email(message = "email không hợp lệ")
    @Size(max = 255, message = "email tối đa 255 ký tự")
    String email;
}
