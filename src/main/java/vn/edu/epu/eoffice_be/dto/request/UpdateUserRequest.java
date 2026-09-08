package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.enums.Role;

@Getter
@Setter
@Schema(description = "Request cập nhật user (tất cả trường đều tùy chọn, chỉ truyền những trường muốn đổi)")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateUserRequest {

    @Schema(description = "Tên đăng nhập mới (duy nhất nếu thay đổi)", example = "nguyen.van.a")
    @Size(max = 100, message = "username tối đa 100 ký tự")
    String username;

    @Schema(description = "Email mới (duy nhất nếu thay đổi)", example = "nguyen.van.a@epu.edu.vn")
    @Email(message = "email không hợp lệ")
    @Size(max = 255, message = "email tối đa 255 ký tự")
    String email;

    @Schema(description = "Vai trò mới. Chỉ SYSTEM_ADMIN được đổi sang/đổi từ SYSTEM_ADMIN.", example = "ADMIN")
    Role role;

    @Schema(description = "Yêu cầu đổi mật khẩu lần đăng nhập tiếp theo")
    Boolean mustChangePassword;
}
