package vn.edu.epu.eoffice_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.enums.Role;

import java.time.Instant;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Kết quả đăng nhập/đăng ký/refresh: accessToken trong body, refreshToken chỉ trả qua cookie HttpOnly (bị ẩn khỏi body)")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LoginResponse {

    @Schema(description = "Access token JWT dùng cho header Authorization: Bearer <token>", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMDAxIiwidXNlcm5hbWUiOiJhZG1pbiJ9.xK9f...")
    String accessToken;

    @JsonIgnore
    String refreshToken;

    @Schema(description = "Thời gian sống của access token (giây)", example = "900")
    Long expiresIn;

    @Schema(description = "Thông tin người dùng sau khi xác thực")
    TokenUser user;

    @Getter
    @Setter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class TokenUser {
        @Schema(description = "Id người dùng", example = "1001")
        Long id;
        @Schema(description = "Tên đăng nhập", example = "admin")
        String username;
        @Schema(description = "Tên hiển thị đầy đủ", example = "Quản trị hệ thống")
        String displayName;
        @Schema(description = "Email liên hệ", example = "admin@epu.edu.vn")
        String email;
        @Schema(description = "Đường dẫn ảnh đại diện", example = "/uploads/avatar/admin.png", nullable = true)
        String avatarUrl;
        @Schema(description = "Role hệ thống", example = "ADMIN")
        Role role;
        @Schema(description = "Thời điểm mật khẩu hết hạn (null = không giới hạn)", example = "2026-11-01T00:00:00Z", nullable = true)
        Instant passwordExpiredAt;
    }
}
