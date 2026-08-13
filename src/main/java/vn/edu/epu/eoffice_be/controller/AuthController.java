package vn.edu.epu.eoffice_be.controller;

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
public class AuthController {

    private final AuthService authService;
    private final JwtProperties jwtProperties;

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

    @GetMapping("/me")
    public BaseResponse<MeResponse> me(Authentication authentication) {
        JwtUserPrincipal principal = (JwtUserPrincipal) authentication.getPrincipal();
        return BaseResponse.success(authService.me(principal.getUserId()));
    }

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
