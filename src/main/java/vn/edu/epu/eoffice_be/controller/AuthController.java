package vn.edu.epu.eoffice_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.config.JwtProperties;
import vn.edu.epu.eoffice_be.dto.request.LoginRequest;
import vn.edu.epu.eoffice_be.dto.request.RegisterRequest;
import vn.edu.epu.eoffice_be.dto.response.LoginResponse;
import vn.edu.epu.eoffice_be.dto.response.MeResponse;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.service.AuthService;
import vn.edu.epu.eoffice_be.utils.AuthHttpUtils;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Xác thực người dùng: đăng nhập, đăng ký, thông tin hiện tại, refresh và logout")
public class AuthController {

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    @Operation(
            summary = "Đăng nhập",
            description = "Xác thực bằng username/password. Trả accessToken trong body; refreshToken gắn qua cookie HttpOnly (Path=/auth). "
                    + "Rate limit theo IP, khóa tài khoản sau 5 lần sai liên tiếp."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng nhập thành công"),
            @ApiResponse(responseCode = "401", description = "Sai tên đăng nhập hoặc mật khẩu (INVALID_CREDENTIALS)"),
            @ApiResponse(responseCode = "423", description = "Tài khoản bị khóa (ACCOUNT_LOCKED)"),
            @ApiResponse(responseCode = "429", description = "Quá nhiều lần thử (RATE_LIMITED)")
    })
    @SecurityRequirements
    @PostMapping("/login")
    public BaseResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        LoginResponse result = authService.login(request, AuthHttpUtils.clientIp(httpRequest));
        AuthHttpUtils.setRefreshTokenCookie(response, result.getRefreshToken(), jwtProperties);
        return BaseResponse.success(result);
    }

    @Operation(
            summary = "Đăng ký tài khoản",
            description = "Tạo tài khoản mới với role USER và tự động đăng nhập. refreshToken gắn qua cookie HttpOnly. "
                    + "Không tạo bản ghi profile (user_infos) — FE dùng profileCompleted ở /auth/me để hiện popup điền hồ sơ."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng ký thành công, đã tự động đăng nhập"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @ApiResponse(responseCode = "409", description = "Username hoặc email đã tồn tại (USERNAME_ALREADY_EXISTS / EMAIL_ALREADY_EXISTS)"),
            @ApiResponse(responseCode = "429", description = "Quá nhiều lần thử (RATE_LIMITED)")
    })
    @SecurityRequirements
    @PostMapping("/register")
    public BaseResponse<LoginResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        LoginResponse result = authService.register(request, AuthHttpUtils.clientIp(httpRequest));
        AuthHttpUtils.setRefreshTokenCookie(response, result.getRefreshToken(), jwtProperties);
        return BaseResponse.success(result);
    }

    @Operation(
            summary = "Thông tin người dùng hiện tại",
            description = "Trả về thông tin tài khoản (user_logins) gộp với hồ sơ bổ sung (user_infos) của người dùng đang xác thực. "
                    + "profileCompleted = true khi đã có bản ghi user_infos."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy thông tin thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực hoặc token không hợp lệ (UNAUTHORIZED / INVALID_TOKEN)")
    })
    @GetMapping("/me")
    public BaseResponse<MeResponse> me(Authentication authentication) {
        JwtUserPrincipal principal = (JwtUserPrincipal) authentication.getPrincipal();
        return BaseResponse.success(authService.me(principal.getUserId()));
    }

    @Operation(
            summary = "Làm mới access token",
            description = "Đọc refreshToken từ cookie (Path=/auth), kiểm tra jti trong bảng refresh_tokens và rotate: cấp cặp AT/RT mới, "
                    + "đánh dấu RT cũ đã thu hồi. RT đã thu hồi xuất hiện lại (reuse) → thu hồi toàn bộ phiên của user. "
                    + "Bảo vệ bằng SameSite=Strict + Origin check."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cấp token mới thành công"),
            @ApiResponse(responseCode = "401", description = "Cookie thiếu/hết hạn/bị thu hồi (SESSION_EXPIRED / INVALID_TOKEN)")
    })
    @SecurityRequirements
    @PostMapping("/refresh")
    public BaseResponse<LoginResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AuthHttpUtils.assertAllowedOrigin(request, jwtProperties.getAllowedOrigins());
        LoginResponse result = authService.refresh(
                AuthHttpUtils.refreshTokenFrom(request, jwtProperties.getCookieName()));
        AuthHttpUtils.setRefreshTokenCookie(response, result.getRefreshToken(), jwtProperties);
        return BaseResponse.success(result);
    }

    @Operation(
            summary = "Đăng xuất",
            description = "Thu hồi refreshToken hiện tại (is_revoked = true) và xóa cookie. Không bao giờ fail — token hết hạn vẫn logout được."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng xuất thành công")
    })
    @SecurityRequirements
    @PostMapping("/logout")
    public BaseResponse<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AuthHttpUtils.assertAllowedOrigin(request, jwtProperties.getAllowedOrigins());
        authService.logout(AuthHttpUtils.refreshTokenFrom(request, jwtProperties.getCookieName()));
        AuthHttpUtils.clearRefreshTokenCookie(response, jwtProperties);
        return BaseResponse.success(null);
    }
}
