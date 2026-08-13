package vn.edu.epu.eoffice_be.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.entity.RefreshToken;
import vn.edu.epu.eoffice_be.repository.RefreshTokenRepository;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RefreshTokenService {

    final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void issue(Long userId, String jti, Instant expiresAt) {
        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .rt(jti)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();
        refreshTokenRepository.save(token);
    }

    @Transactional
    public void rotate(Long userId, String oldJti, String newJti, Instant newExpiresAt) {
        RefreshToken old = refreshTokenRepository.findByRt(oldJti)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_EXPIRED));

        if (old.isRevoked()) {
            log.warn("Refresh token reuse detected for jti={}, userId={}; revoking all active tokens",
                    oldJti, old.getUserId());
            refreshTokenRepository.revokeAllActiveByUserId(old.getUserId());
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }
        if (!old.getUserId().equals(userId)) {
            log.warn("Refresh token jti={} belongs to userId={} but presented by userId={}",
                    oldJti, old.getUserId(), userId);
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }
        if (old.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }

        old.setRevoked(true);
        refreshTokenRepository.save(old);
        issue(userId, newJti, newExpiresAt);
    }

    @Transactional
    public void revoke(String jti) {
        refreshTokenRepository.findByRt(jti).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            }
        });
    }

    @Transactional
    public int revokeAllActive(Long userId) {
        int revoked = refreshTokenRepository.revokeAllActiveByUserId(userId);
        if (revoked > 0) {
            log.info("Revoked {} active refresh tokens for userId={}", revoked, userId);
        }
        return revoked;
    }
}
