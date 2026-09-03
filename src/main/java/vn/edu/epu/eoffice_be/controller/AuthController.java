package vn.edu.epu.eoffice_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
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
import vn.edu.epu.eoffice_be.dto.request.ForgotPasswordRequest;
import vn.edu.epu.eoffice_be.dto.request.LoginRequest;
import vn.edu.epu.eoffice_be.dto.request.RegisterRequest;
import vn.edu.epu.eoffice_be.dto.request.ResetPasswordRequest;
import vn.edu.epu.eoffice_be.dto.response.LoginResponse;
import vn.edu.epu.eoffice_be.dto.response.MeResponse;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.service.AuthService;
import vn.edu.epu.eoffice_be.service.PasswordResetService;
import vn.edu.epu.eoffice_be.utils.AuthHttpUtils;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Xác thực người dùng: đăng nhập, đăng ký, thông tin hiện tại, refresh và logout")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
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
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Thông tin đăng nhập",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = LoginRequest.class),
                    examples = @ExampleObject(value = """
                            {
                              "username": "admin",
                              "password": "Admin@1234"
                            }""")))
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
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Thông tin đăng ký tài khoản mới",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RegisterRequest.class),
                    examples = @ExampleObject(value = """
                            {
                              "username": "nguyen.van.a",
                              "email": "nguyen.van.a@epu.edu.vn",
                              "password": "MatKhau@2026"
                            }""")))
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
        return BaseResponse.success(authService.me(principal.userId()));
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

    @Operation(
            summary = "Yêu cầu đặt lại mật khẩu",
            description = "Nhận email đăng nhập và gửi link đặt lại mật khẩu nếu email tồn tại. Response luôn thành công để tránh dò tài khoản."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã tiếp nhận yêu cầu"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ")
    })
    @SecurityRequirements
    @PostMapping("/forgot-password")
    public BaseResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.forgotPassword(request);
        return BaseResponse.success(null, "Nếu email tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi");
    }

    @Operation(
            summary = "Đặt lại mật khẩu",
            description = "Đặt mật khẩu mới bằng token lấy từ link quên mật khẩu. Token chỉ dùng một lần và có thời hạn."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đặt lại mật khẩu thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ hoặc token không hợp lệ/hết hạn")
    })
    @SecurityRequirements
    @PostMapping("/reset-password")
    public BaseResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return BaseResponse.success(null, "Đặt lại mật khẩu thành công");
    }
}
