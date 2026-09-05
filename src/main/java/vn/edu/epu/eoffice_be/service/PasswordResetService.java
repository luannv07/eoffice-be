package vn.edu.epu.eoffice_be.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.UserStatusCd;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.config.PasswordResetProperties;
import vn.edu.epu.eoffice_be.dto.request.ForgotPasswordRequest;
import vn.edu.epu.eoffice_be.dto.request.ResetPasswordRequest;
import vn.edu.epu.eoffice_be.entity.PasswordResetToken;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.PasswordResetTokenRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.service.mail.MailSender;
import vn.edu.epu.eoffice_be.service.mail.template.PasswordResetMailTemplate;
import vn.edu.epu.eoffice_be.utils.SecureTokenUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PasswordResetService {

    static final int RESET_TOKEN_BYTES = 32;

    UserLoginRepository userLoginRepository;
    PasswordResetTokenRepository passwordResetTokenRepository;
    PasswordEncoder passwordEncoder;
    RefreshTokenService refreshTokenService;
    MailSender mailSender;
    PasswordResetMailTemplate passwordResetMailTemplate;
    PasswordResetProperties properties;

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().trim();
        Optional<UserLogin> userLogin =
                userLoginRepository.findByEmailAndDeleteFlgFalse(email);
        if (userLogin.isEmpty()) {
            log.info("[PASSWORD_RESET] Email [{}] does not exist", email);
            return;
        }
        UserLogin user = userLogin.get();
        Instant now = Instant.now();
        String rawToken = SecureTokenUtils.generateUrlSafeToken(RESET_TOKEN_BYTES);
        passwordResetTokenRepository.markUnusedTokensUsedByUserId(
                user.getId(),
                now
        );
        passwordResetTokenRepository.save(
                PasswordResetToken.builder()
                        .userLogin(user)
                        .tokenHash(SecureTokenUtils.sha256Hex(rawToken))
                        .expiresAt(now.plus(
                                Duration.ofMinutes(properties.getTokenTtlMinutes())
                        ))
                        .build()
        );
        String resetLink = resetLink(rawToken);
        PasswordResetMailTemplate.Context context =
                new PasswordResetMailTemplate.Context(
                        resetLink,
                        properties.getTokenTtlMinutes()
                );
        log.info("[PASSWORD_RESET] Email [{}] exists, sending reset email", email);
        mailSender.send(
                user.getEmail(),
                passwordResetMailTemplate.subject(context),
                passwordResetMailTemplate.htmlBody(context)
        );
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHashAndDeleteFlgFalse(SecureTokenUtils.sha256Hex(request.getToken()))
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
        Instant now = Instant.now();
        if (token.getUsedAt() != null) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_EXPIRED);
        }

        UserLogin user = token.getUserLogin();
        if (user == null || user.isDeleteFlg() || UserStatusCd.DISABLED.name().equals(user.getUserStatusCd())) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        if (UserStatusCd.LOCKED.name().equals(user.getUserStatusCd())) {
            user.setUserStatusCd(UserStatusCd.ACTIVE.name());
        }
        token.setUsedAt(now);
        refreshTokenService.revokeAllActive(user.getId());
    }

    private String resetLink(String rawToken) {
        return UriComponentsBuilder.fromUriString(properties.getResetPasswordUrl())
                .queryParam("token", rawToken)
                .build()
                .toUriString();
    }
}
