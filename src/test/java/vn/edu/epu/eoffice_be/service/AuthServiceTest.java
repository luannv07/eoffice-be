package vn.edu.epu.eoffice_be.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	static final String CLIENT_IP = "1.2.3.4";

	@Mock
	UserLoginRepository userLoginRepository;
	@Mock
	UserInfoRepository userInfoRepository;
	@Mock
	PasswordEncoder passwordEncoder;
	@Mock
	JwtService jwtService;
	@Mock
	JwtProperties jwtProperties;
	@Mock
	RefreshTokenService refreshTokenService;
	@Mock
	vn.edu.epu.eoffice_be.repository.PositionAssignmentRepository positionAssignmentRepository;

	@InjectMocks
	AuthService authService;

	@Test
	void registerShouldReturnTokensWithEncodedPasswordAndActiveUser() {
		when(userLoginRepository.existsByUsernameAndDeleteFlgFalse("alice")).thenReturn(false);
		when(userLoginRepository.existsByEmailAndDeleteFlgFalse("alice@example.com")).thenReturn(false);
		when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$encodedHash");
		when(jwtProperties.getAccessTokenTtl()).thenReturn(Duration.ofMinutes(15));
		when(jwtProperties.getRefreshTokenTtl()).thenReturn(Duration.ofDays(30));
		when(userLoginRepository.saveAndFlush(any(UserLogin.class))).thenAnswer(invocation -> {
			UserLogin saved = invocation.getArgument(0);
			saved.setId(10L);
			return saved;
		});
		when(jwtService.generateAccessToken(10L, "alice", Role.USER, false)).thenReturn("access-token");
		when(jwtService.generateRefreshToken(10L, "alice", Role.USER)).thenReturn("refresh-token");
		when(jwtService.parseRefreshToken("refresh-token")).thenReturn(claimsWithJti("rt-jti", "10"));

		LoginResponse response = authService.register(registerRequest("alice", "alice@example.com", "secret123"), CLIENT_IP);

		assertEquals("access-token", response.getAccessToken());
		assertEquals("refresh-token", response.getRefreshToken());
		assertEquals(900L, response.getExpiresIn());
		assertEquals(10L, response.getUser().getId());
		assertEquals("alice", response.getUser().getUsername());
		assertEquals("alice@example.com", response.getUser().getDisplayName());
		assertEquals(Role.USER, response.getUser().getRole());

		verify(userLoginRepository).saveAndFlush(any(UserLogin.class));
		verify(refreshTokenService).issue(eq(10L), eq("rt-jti"), any(Instant.class));
	}

	@Test
	void registerDuplicateUsernameShouldThrowConflict() {
		when(userLoginRepository.existsByUsernameAndDeleteFlgFalse("alice")).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
						() -> authService.register(registerRequest("alice", "alice@example.com", "secret123"), CLIENT_IP));

		assertEquals(ErrorCode.USERNAME_ALREADY_EXISTS, ex.getErrorCode());
		verify(userLoginRepository, never()).saveAndFlush(any());
	}

	@Test
	void registerDuplicateEmailShouldThrowConflict() {
		when(userLoginRepository.existsByUsernameAndDeleteFlgFalse("alice")).thenReturn(false);
		when(userLoginRepository.existsByEmailAndDeleteFlgFalse("alice@example.com")).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
						() -> authService.register(registerRequest("alice", "alice@example.com", "secret123"), CLIENT_IP));

		assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, ex.getErrorCode());
		verify(userLoginRepository, never()).saveAndFlush(any());
	}

	@Test
	void registerConstraintViolationRaceShouldMapToDuplicateError() {
		when(userLoginRepository.existsByUsernameAndDeleteFlgFalse("alice"))
						.thenReturn(false)
						.thenReturn(true);
		when(userLoginRepository.existsByEmailAndDeleteFlgFalse("alice@example.com")).thenReturn(false);
		when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$encodedHash");
		when(userLoginRepository.saveAndFlush(any(UserLogin.class)))
						.thenThrow(new DataIntegrityViolationException("duplicate key"));

		BusinessException ex = assertThrows(BusinessException.class,
						() -> authService.register(registerRequest("alice", "alice@example.com", "secret123"), CLIENT_IP));

		assertEquals(ErrorCode.USERNAME_ALREADY_EXISTS, ex.getErrorCode());
	}

	@Test
	void loginShouldRevokeExistingActiveTokensAndIssueNewRefreshToken() {
		UserLogin user = user("alice", "alice@example.com", Role.USER);
		when(userLoginRepository.findByUsernameAndDeleteFlgFalse("alice")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("secret123", "$2a$10$hash")).thenReturn(true);
		when(jwtProperties.getAccessTokenTtl()).thenReturn(Duration.ofMinutes(15));
		when(jwtProperties.getRefreshTokenTtl()).thenReturn(Duration.ofDays(30));
		when(jwtService.generateAccessToken(10L, "alice", Role.USER, false)).thenReturn("access-token");
		when(jwtService.generateRefreshToken(10L, "alice", Role.USER)).thenReturn("refresh-token");
		when(jwtService.parseRefreshToken("refresh-token")).thenReturn(claimsWithJti("rt-jti", "10"));
		when(userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(10L)).thenReturn(Optional.empty());

		LoginRequest request = new LoginRequest();
		request.setUsername("alice");
		request.setPassword("secret123");
		LoginResponse response = authService.login(request, CLIENT_IP);

		assertEquals("access-token", response.getAccessToken());
		assertEquals("refresh-token", response.getRefreshToken());
		assertEquals(900L, response.getExpiresIn());
		assertEquals(10L, response.getUser().getId());

		verify(refreshTokenService).revokeAllActive(10L);
		verify(refreshTokenService).issue(eq(10L), eq("rt-jti"), any(Instant.class));
	}

	@Test
	void meShouldMergeUserLoginAndUserInfo() {
		UserLogin user = user("alice", "alice@example.com", Role.USER);
		UserInfo info = UserInfo.builder()
						.userLogin(user)
						.fullName("Alice Nguyễn")
						.dateOfBirth(LocalDate.of(1995, 5, 10))
						.gender("FEMALE")
						.contactPhone("0901234567")
						.address("Hà Nội")
						.employeeCode("CB001")
						.build();
		when(userLoginRepository.findByIdAndDeleteFlgFalse(10L)).thenReturn(Optional.of(user));
		when(userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(10L)).thenReturn(Optional.of(info));

		MeResponse response = authService.me(10L);

		assertEquals(10L, response.getId());
		assertEquals("alice", response.getUsername());
		assertEquals("alice@example.com", response.getEmail());
		assertEquals(Role.USER, response.getRole());
		assertEquals(UserStatusCd.ACTIVE.name(), response.getUserStatusCd());
		assertEquals("Alice Nguyễn", response.getFullName());
		assertEquals(LocalDate.of(1995, 5, 10), response.getDateOfBirth());
		assertEquals("FEMALE", response.getGender());
		assertEquals("0901234567", response.getContactPhone());
		assertEquals("Hà Nội", response.getAddress());
		assertEquals("CB001", response.getEmployeeCode());
		assertTrue(response.isProfileCompleted());
	}

	@Test
	void meWithoutUserInfoShouldReturnProfileCompletedFalseAndNullInfoFields() {
		UserLogin user = user("alice", "alice@example.com", Role.USER);
		when(userLoginRepository.findByIdAndDeleteFlgFalse(10L)).thenReturn(Optional.of(user));
		when(userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(10L)).thenReturn(Optional.empty());

		MeResponse response = authService.me(10L);

		assertEquals("alice", response.getUsername());
		assertFalse(response.isProfileCompleted());
		assertNull(response.getFullName());
		assertNull(response.getAvatarUrl());
		assertNull(response.getEmployeeCode());
	}

	@Test
	void meUnknownUserShouldThrowNotFound() {
		when(userLoginRepository.findByIdAndDeleteFlgFalse(99L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> authService.me(99L));

		assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
	}

	@Test
	void refreshShouldRotateRefreshTokenAndReturnNewTokens() {
		UserLogin user = user("alice", "alice@example.com", Role.USER);
		when(jwtService.parseRefreshToken("old-rt")).thenReturn(claimsWithJti("old-jti", "10"));
		when(userLoginRepository.findByIdAndDeleteFlgFalse(10L)).thenReturn(Optional.of(user));
		when(jwtService.generateRefreshToken(10L, "alice", Role.USER)).thenReturn("new-rt");
		when(jwtService.parseRefreshToken("new-rt")).thenReturn(claimsWithJti("new-jti", "10"));
		when(jwtProperties.getRefreshTokenTtl()).thenReturn(Duration.ofDays(30));
		when(jwtService.generateAccessToken(10L, "alice", Role.USER, false)).thenReturn("new-at");
		when(jwtProperties.getAccessTokenTtl()).thenReturn(Duration.ofMinutes(15));
		when(userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(10L)).thenReturn(Optional.empty());

		LoginResponse response = authService.refresh("old-rt");

		assertEquals("new-at", response.getAccessToken());
		assertEquals("new-rt", response.getRefreshToken());
		assertEquals(900L, response.getExpiresIn());
		verify(refreshTokenService).rotate(eq(10L), eq("old-jti"), eq("new-jti"), any(Instant.class));
	}

	@Test
	void refreshWithBlankTokenShouldThrowSessionExpired() {
		BusinessException ex = assertThrows(BusinessException.class, () -> authService.refresh(null));

		assertEquals(ErrorCode.SESSION_EXPIRED, ex.getErrorCode());
		verify(jwtService, never()).parseRefreshToken(any());
		verify(refreshTokenService, never()).rotate(any(), any(), any(), any());
	}

	@Test
	void refreshWithInvalidTokenShouldThrowSessionExpired() {
		when(jwtService.parseRefreshToken("garbage")).thenThrow(new JwtException("bad token"));

		BusinessException ex = assertThrows(BusinessException.class, () -> authService.refresh("garbage"));

		assertEquals(ErrorCode.SESSION_EXPIRED, ex.getErrorCode());
		verify(refreshTokenService, never()).rotate(any(), any(), any(), any());
	}

	@Test
	void refreshWithDisabledUserShouldThrowAccountDisabled() {
		UserLogin user = user("alice", "alice@example.com", Role.USER);
		user.setUserStatusCd(UserStatusCd.DISABLED.name());
		when(jwtService.parseRefreshToken("old-rt")).thenReturn(claimsWithJti("old-jti", "10"));
		when(userLoginRepository.findByIdAndDeleteFlgFalse(10L)).thenReturn(Optional.of(user));

		BusinessException ex = assertThrows(BusinessException.class, () -> authService.refresh("old-rt"));

		assertEquals(ErrorCode.ACCOUNT_DISABLED, ex.getErrorCode());
		verify(refreshTokenService, never()).rotate(any(), any(), any(), any());
	}

	@Test
	void logoutShouldRevokeJtiOfValidToken() {
		when(jwtService.parseRefreshToken("rt")).thenReturn(claimsWithJti("jti-1", "10"));

		authService.logout("rt");

		verify(refreshTokenService).revoke("jti-1");
	}

	@Test
	void logoutWithExpiredTokenShouldStillRevokeUsingExpiredClaims() {
		Claims claims = claimsWithJti("jti-expired", "10");
		when(jwtService.parseRefreshToken("expired-rt")).thenThrow(new ExpiredJwtException(null, claims, "expired"));

		authService.logout("expired-rt");

		verify(refreshTokenService).revoke("jti-expired");
	}

	@Test
	void logoutWithUnparsableOrBlankTokenShouldBeNoOp() {
		when(jwtService.parseRefreshToken("garbage")).thenThrow(new JwtException("bad token"));

		authService.logout("garbage");
		authService.logout(null);
		authService.logout("  ");

		verify(refreshTokenService, never()).revoke(any());
	}

	private Claims claimsWithJti(String jti, String subject) {
		return Jwts.claims().id(jti).add("sub", subject).build();
	}

	private RegisterRequest registerRequest(String username, String email, String password) {
		RegisterRequest request = new RegisterRequest();
		request.setUsername(username);
		request.setEmail(email);
		request.setPassword(password);
		return request;
	}

	private UserLogin user(String username, String email, Role role) {
		return UserLogin.builder()
						.id(10L)
						.username(username)
						.email(email)
						.passwordHash("$2a$10$hash")
						.role(role)
						.userStatusCd(UserStatusCd.ACTIVE.name())
						.lastLoginAt(Instant.parse("2026-08-10T10:00:00Z"))
						.build();
	}
}
