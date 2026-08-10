package vn.edu.epu.eoffice_be.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.config.JwtProperties;
import vn.edu.epu.eoffice_be.dto.request.LoginRequest;
import vn.edu.epu.eoffice_be.dto.response.LoginResponse;
import vn.edu.epu.eoffice_be.dto.response.MeResponse;
import vn.edu.epu.eoffice_be.entity.UserInfo;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.security.LoginRateLimiter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCKOUT_DURATION = Duration.ofMinutes(30);

    final UserLoginRepository userLoginRepository;
    final UserInfoRepository userInfoRepository;
    final PasswordEncoder passwordEncoder;
    final JwtService jwtService;
    final JwtProperties jwtProperties;
    final LoginRateLimiter loginRateLimiter;

    @Transactional
    public LoginResponse login(LoginRequest request, String clientIp) {
        if (!loginRateLimiter.tryAcquire(clientIp)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }

        UserLogin user = userLoginRepository.findByUsernameAndDeleteFlgFalse(request.getUsername().trim())
                .orElseThrow(() -> {
                    recordFailedAttempt(clientIp, null);
                    return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
                });

        switch (user.getUserStatusCd()) {
            case "LOCKED" -> throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
            case "DISABLED" -> throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
            default -> { }
        }
        if (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            recordFailedAttempt(clientIp, user);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        resetFailedAttempts(user);
        user.setLastLoginAt(Instant.now());
        userLoginRepository.save(user);

        if (user.getPasswordExpiredAt() != null && user.getPasswordExpiredAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.PASSWORD_EXPIRED);
        }

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());

        UserInfo info = userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(user.getId()).orElse(null);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtProperties.getAccessTokenTtl().toSeconds())
                .user(toTokenUser(user, info))
                .build();
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        UserLogin user = userLoginRepository.findByIdAndDeleteFlgFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        UserInfo info = userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(userId).orElse(null);
        return toMeResponse(user, info);
    }

    private void recordFailedAttempt(String clientIp, UserLogin user) {
        log.warn("Failed login attempt for username={} from ip={}", user == null ? "(unknown)" : user.getUsername(), clientIp);
        if (user == null) {
            return;
        }
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        if (user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
            user.setUserStatusCd("LOCKED");
            user.setLockoutUntil(Instant.now().plus(LOCKOUT_DURATION));
        }
        userLoginRepository.save(user);
    }

    private void resetFailedAttempts(UserLogin user) {
        user.setFailedLoginAttempts(0);
        user.setUserStatusCd("ACTIVE");
        user.setLockoutUntil(null);
    }

    private LoginResponse.TokenUser toTokenUser(UserLogin user, UserInfo info) {
        return LoginResponse.TokenUser.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(displayName(user, info))
                .email(user.getEmail())
                .avatarUrl(info == null ? null : info.getAvatarUrl())
                .role(user.getRole())
                .passwordExpiredAt(user.getPasswordExpiredAt())
                .build();
    }

    private MeResponse toMeResponse(UserLogin user, UserInfo info) {
        return MeResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(displayName(user, info))
                .email(user.getEmail())
                .avatarUrl(info == null ? null : info.getAvatarUrl())
                .role(user.getRole())
                .passwordExpiredAt(user.getPasswordExpiredAt())
                .build();
    }

    private String displayName(UserLogin user, UserInfo info) {
        if (info != null && info.getFullName() != null && !info.getFullName().isBlank()) {
            return info.getFullName();
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        return user.getUsername();
    }
}