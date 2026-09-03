package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    UserLoginRepository userLoginRepository;
    @Mock
    PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    RefreshTokenService refreshTokenService;
    @Mock
    MailSender mailSender;
    @Mock
    PasswordResetMailTemplate passwordResetMailTemplate;
    @Mock
    PasswordResetProperties properties;

    @InjectMocks
    PasswordResetService passwordResetService;

    @Test
    void forgotPasswordShouldSendResetLinkWhenEmailExists() {
        UserLogin user = user();
        when(userLoginRepository.findByEmailAndDeleteFlgFalse("alice@example.com")).thenReturn(Optional.of(user));
        when(properties.getTokenTtlMinutes()).thenReturn(15);
        when(properties.getResetPasswordUrl()).thenReturn("http://localhost:5173/reset-password");
        when(passwordResetMailTemplate.subject(any())).thenReturn("Đặt lại mật khẩu eOffice");
        when(passwordResetMailTemplate.htmlBody(any())).thenReturn("<a href=\"http://localhost:5173/reset-password?token=test\">Reset</a>");

        passwordResetService.forgotPassword(request("alice@example.com"));

        verify(passwordResetTokenRepository).markUnusedTokensUsedByUserId(eq(10L), any(Instant.class));
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(mailSender).send(eq("alice@example.com"), eq("Đặt lại mật khẩu eOffice"), org.mockito.ArgumentMatchers.contains("token="));
    }

    @Test
    void forgotPasswordShouldNotRevealUnknownEmail() {
        when(userLoginRepository.findByEmailAndDeleteFlgFalse("missing@example.com")).thenReturn(Optional.empty());

        passwordResetService.forgotPassword(request("missing@example.com"));

        verify(passwordResetTokenRepository, never()).save(any());
        verify(mailSender, never()).send(any(), any(), any());
    }

    @Test
    void resetPasswordShouldUpdatePasswordUseTokenAndRevokeSessions() {
        UserLogin user = user();
        PasswordResetToken token = PasswordResetToken.builder()
                .userLogin(user)
                .tokenHash(SecureTokenUtils.sha256Hex("raw-token"))
                .expiresAt(Instant.now().plus(Duration.ofMinutes(10)))
                .build();
        when(passwordResetTokenRepository.findByTokenHashAndDeleteFlgFalse(SecureTokenUtils.sha256Hex("raw-token")))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPassword@2026")).thenReturn("encoded-new-password");

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("raw-token");
        request.setNewPassword("NewPassword@2026");
        passwordResetService.resetPassword(request);

        assertEquals("encoded-new-password", user.getPasswordHash());
        assertNotNull(token.getUsedAt());
        assertEquals(0, user.getFailedLoginAttempts());
        assertEquals(null, user.getLockoutUntil());
        verify(refreshTokenService).revokeAllActive(10L);
    }

    @Test
    void resetPasswordShouldRejectExpiredToken() {
        PasswordResetToken token = PasswordResetToken.builder()
                .userLogin(user())
                .tokenHash(SecureTokenUtils.sha256Hex("raw-token"))
                .expiresAt(Instant.now().minus(Duration.ofMinutes(1)))
                .build();
        when(passwordResetTokenRepository.findByTokenHashAndDeleteFlgFalse(SecureTokenUtils.sha256Hex("raw-token")))
                .thenReturn(Optional.of(token));

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("raw-token");
        request.setNewPassword("NewPassword@2026");

        BusinessException ex = assertThrows(BusinessException.class, () -> passwordResetService.resetPassword(request));

        assertEquals(ErrorCode.PASSWORD_RESET_TOKEN_EXPIRED, ex.getErrorCode());
        assertTrue(token.isDeleteFlg());
    }

    private ForgotPasswordRequest request(String email) {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(email);
        return request;
    }

    private UserLogin user() {
        UserLogin user = UserLogin.builder()
                .username("alice")
                .email("alice@example.com")
                .passwordHash("old-password")
                .build();
        user.setId(10L);
        user.setFailedLoginAttempts(3);
        user.setLockoutUntil(Instant.now().plus(Duration.ofMinutes(5)));
        return user;
    }
}
