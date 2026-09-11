package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.entity.RefreshToken;
import vn.edu.epu.eoffice_be.repository.RefreshTokenRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

	@Mock
	RefreshTokenRepository refreshTokenRepository;

	@InjectMocks
	RefreshTokenService refreshTokenService;

	@Test
	void issueShouldPersistActiveToken() {
		refreshTokenService.issue(10L, "jti-1", Instant.parse("2026-09-11T00:00:00Z"));

		ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository).save(captor.capture());
		RefreshToken saved = captor.getValue();
		assertEquals(10L, saved.getUserId());
		assertEquals("jti-1", saved.getRt());
		assertEquals(Instant.parse("2026-09-11T00:00:00Z"), saved.getExpiresAt());
		assertFalse(saved.isRevoked());
	}

	@Test
	void rotateShouldRevokeOldAndIssueNewToken() {
		RefreshToken old = activeToken(10L, "old-jti", Instant.now().plusSeconds(3600));
		when(refreshTokenRepository.findByRt("old-jti")).thenReturn(Optional.of(old));

		refreshTokenService.rotate(10L, "old-jti", "new-jti", Instant.now().plusSeconds(7200));

		assertTrue(old.isRevoked());
		ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository, times(2)).save(captor.capture());
		List<RefreshToken> saved = captor.getAllValues();
		assertTrue(saved.get(0).isRevoked());
		assertEquals("new-jti", saved.get(1).getRt());
		assertFalse(saved.get(1).isRevoked());
	}

	@Test
	void rotateWithUnknownJtiShouldThrowSessionExpired() {
		when(refreshTokenRepository.findByRt("unknown-jti")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
						() -> refreshTokenService.rotate(10L, "unknown-jti", "new-jti", Instant.parse("2026-10-11T00:00:00Z")));

		assertEquals(ErrorCode.SESSION_EXPIRED, ex.getErrorCode());
		verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
	}

	@Test
	void rotateWithRevokedTokenShouldRevokeFamilyAndThrowSessionExpired() {
		RefreshToken revoked = RefreshToken.builder()
						.userId(10L)
						.rt("stolen-jti")
						.expiresAt(Instant.parse("2026-09-11T00:00:00Z"))
						.revoked(true)
						.build();
		when(refreshTokenRepository.findByRt("stolen-jti")).thenReturn(Optional.of(revoked));

		BusinessException ex = assertThrows(BusinessException.class,
						() -> refreshTokenService.rotate(10L, "stolen-jti", "new-jti", Instant.parse("2026-10-11T00:00:00Z")));

		assertEquals(ErrorCode.SESSION_EXPIRED, ex.getErrorCode());
		verify(refreshTokenRepository).revokeAllActiveByUserId(10L);
	}

	@Test
	void rotateWithExpiredTokenShouldThrowSessionExpiredWithoutFamilyRevoke() {
		RefreshToken expired = activeToken(10L, "expired-jti", Instant.parse("2026-07-01T00:00:00Z"));
		when(refreshTokenRepository.findByRt("expired-jti")).thenReturn(Optional.of(expired));

		BusinessException ex = assertThrows(BusinessException.class,
						() -> refreshTokenService.rotate(10L, "expired-jti", "new-jti", Instant.parse("2026-10-11T00:00:00Z")));

		assertEquals(ErrorCode.SESSION_EXPIRED, ex.getErrorCode());
		verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any());
	}

	@Test
	void rotateWithUserIdMismatchShouldThrowSessionExpired() {
		RefreshToken otherUserToken = activeToken(99L, "other-jti", Instant.parse("2026-09-11T00:00:00Z"));
		when(refreshTokenRepository.findByRt("other-jti")).thenReturn(Optional.of(otherUserToken));

		BusinessException ex = assertThrows(BusinessException.class,
						() -> refreshTokenService.rotate(10L, "other-jti", "new-jti", Instant.parse("2026-10-11T00:00:00Z")));

		assertEquals(ErrorCode.SESSION_EXPIRED, ex.getErrorCode());
		verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any());
	}

	@Test
	void revokeShouldMarkExistingTokenAsRevoked() {
		RefreshToken token = activeToken(10L, "jti-1", Instant.parse("2026-09-11T00:00:00Z"));
		when(refreshTokenRepository.findByRt("jti-1")).thenReturn(Optional.of(token));

		refreshTokenService.revoke("jti-1");

		assertTrue(token.isRevoked());
		verify(refreshTokenRepository).save(token);
	}

	@Test
	void revokeWithUnknownJtiShouldBeNoOp() {
		when(refreshTokenRepository.findByRt("missing")).thenReturn(Optional.empty());

		refreshTokenService.revoke("missing");

		verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
	}

	private RefreshToken activeToken(Long userId, String rt, Instant expiresAt) {
		return RefreshToken.builder()
						.userId(userId)
						.rt(rt)
						.expiresAt(expiresAt)
						.revoked(false)
						.build();
	}
}
