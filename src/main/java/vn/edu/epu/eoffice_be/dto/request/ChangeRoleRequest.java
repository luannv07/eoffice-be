package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.enums.Role;

@Getter
@Setter
@Schema(description = "Request đổi vai trò user. Chỉ SYSTEM_ADMIN được đổi sang/đổi từ SYSTEM_ADMIN; "
        + "không thể hạ cấp tài khoản SYSTEM_ADMIN cuối cùng.")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangeRoleRequest {

    @Schema(description = "Vai trò mới", example = "ADMIN", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "role không được để trống")
    Role role;
}
