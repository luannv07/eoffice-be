package vn.edu.epu.eoffice_be.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.enums.UserStatusCd;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.config.JwtProperties;
import vn.edu.epu.eoffice_be.dto.request.LoginRequest;
import vn.edu.epu.eoffice_be.dto.request.RegisterRequest;
import vn.edu.epu.eoffice_be.dto.response.LoginResponse;
import vn.edu.epu.eoffice_be.dto.response.MeResponse;
import vn.edu.epu.eoffice_be.entity.UserInfo;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.security.LoginRateLimiter;
import vn.edu.epu.eoffice_be.utils.AccountStatusUtils;
import org.springframework.dao.DataIntegrityViolationException;
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
    final RefreshTokenService refreshTokenService;

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

        AccountStatusUtils.checkLoginAllowed(user, Instant.now());

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
        refreshTokenService.revokeAllActive(user.getId());
        persistRefreshToken(user.getId(), refreshToken);

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

    @Transactional
    public LoginResponse register(RegisterRequest request, String clientIp) {
        if (!loginRateLimiter.tryAcquire(clientIp)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }

        String username = request.getUsername().trim();
        String email = request.getEmail().trim();

        if (userLoginRepository.existsByUsernameAndDeleteFlgFalse(username)) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        if (userLoginRepository.existsByEmailAndDeleteFlgFalse(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        UserLogin user = UserLogin.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .userStatusCd(UserStatusCd.ACTIVE.name())
                .build();

        try {
            user = userLoginRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            if (userLoginRepository.existsByUsernameAndDeleteFlgFalse(username)) {
                throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
            }
            if (userLoginRepository.existsByEmailAndDeleteFlgFalse(email)) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
            throw ex;
        }

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());
        persistRefreshToken(user.getId(), refreshToken);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtProperties.getAccessTokenTtl().toSeconds())
                .user(toTokenUser(user, null))
                .build();
    }

    @Transactional
    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }
        Instant now = Instant.now();
        Claims claims;
        try {
            claims = jwtService.parseRefreshToken(refreshToken);
        } catch (JwtException ex) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }

        Long userId = Long.valueOf(claims.getSubject());
        UserLogin user = userLoginRepository.findByIdAndDeleteFlgFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_EXPIRED));

        AccountStatusUtils.checkLoginAllowed(user, now);

        String newRefreshToken = jwtService.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());
        refreshTokenService.rotate(
                userId,
                claims.getId(),
                jwtService.parseRefreshToken(newRefreshToken).getId(),
                now.plus(jwtProperties.getRefreshTokenTtl()));
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getRole());

        UserInfo info = userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(userId).orElse(null);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtProperties.getAccessTokenTtl().toSeconds())
                .user(toTokenUser(user, info))
                .build();
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String jti;
        try {
            jti = jwtService.parseRefreshToken(refreshToken).getId();
        } catch (ExpiredJwtException ex) {
            jti = ex.getClaims().getId();
        } catch (JwtException ex) {
            log.debug("Ignoring logout with unparsable refresh token: {}", ex.getMessage());
            return;
        }
        refreshTokenService.revoke(jti);
    }

    private void persistRefreshToken(Long userId, String refreshToken) {
        String jti = jwtService.parseRefreshToken(refreshToken).getId();
        refreshTokenService.issue(userId, jti, Instant.now().plus(jwtProperties.getRefreshTokenTtl()));
    }

    private void recordFailedAttempt(String clientIp, UserLogin user) {
        log.warn("Failed login attempt for username={} from ip={}", user == null ? "(unknown)" : user.getUsername(), clientIp);
        if (user == null) {
            return;
        }
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        if (user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
            user.setUserStatusCd(UserStatusCd.LOCKED.name());
            user.setLockoutUntil(Instant.now().plus(LOCKOUT_DURATION));
        }
        userLoginRepository.save(user);
    }

    private void resetFailedAttempts(UserLogin user) {
        user.setFailedLoginAttempts(0);
        user.setUserStatusCd(UserStatusCd.ACTIVE.name());
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
        MeResponse.MeResponseBuilder builder = MeResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .userStatusCd(user.getUserStatusCd())
                .mustChangePassword(user.isMustChangePassword())
                .passwordExpiredAt(user.getPasswordExpiredAt())
                .lastLoginAt(user.getLastLoginAt())
                .profileCompleted(info != null);
        if (info != null) {
            builder.fullName(info.getFullName())
                    .dateOfBirth(info.getDateOfBirth())
                    .gender(info.getGender())
                    .nationalIdNumber(info.getNationalIdNumber())
                    .avatarUrl(info.getAvatarUrl())
                    .contactEmail(info.getContactEmail())
                    .contactPhone(info.getContactPhone())
                    .address(info.getAddress())
                    .employeeCode(info.getEmployeeCode())
                    .academicTitle(info.getAcademicTitle())
                    .academicDegree(info.getAcademicDegree())
                    .partyMembershipDate(info.getPartyMembershipDate())
                    .employmentStatusCd(info.getEmploymentStatusCd())
                    .joinOrgDate(info.getJoinOrgDate());
        }
        return builder.build();
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