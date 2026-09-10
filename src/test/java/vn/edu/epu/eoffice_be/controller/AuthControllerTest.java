package vn.edu.epu.eoffice_be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.epu.eoffice_be.common.exception.GlobalExceptionHandler;
import vn.edu.epu.eoffice_be.config.JwtProperties;
import vn.edu.epu.eoffice_be.dto.request.LoginRequest;
import vn.edu.epu.eoffice_be.dto.response.LoginResponse;
import vn.edu.epu.eoffice_be.service.AuthService;
import vn.edu.epu.eoffice_be.service.PasswordResetService;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

	@Mock
	AuthService authService;
	@Mock
	PasswordResetService passwordResetService;

	JwtProperties jwtProperties;
	MockMvc mockMvc;
	ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		jwtProperties = new JwtProperties();
		mockMvc = MockMvcBuilders
						.standaloneSetup(new AuthController(authService, passwordResetService, jwtProperties))
						.setControllerAdvice(new GlobalExceptionHandler())
						.build();
	}

	@Test
	void loginShouldSetRefreshTokenCookieAndOmitTokenFromBody() throws Exception {
		LoginRequest request = new LoginRequest();
		request.setUsername("alice");
		request.setPassword("secret123");
		LoginResponse result = LoginResponse.builder()
						.accessToken("at-1")
						.refreshToken("rt-1")
						.expiresIn(900L)
						.build();
		when(authService.login(any(LoginRequest.class), anyString())).thenReturn(result);

		mockMvc.perform(post("/auth/login")
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(request)))
						.andExpect(status().isOk())
						.andExpect(header().string("Set-Cookie",
										containsString("refresh_token=rt-1; Path=/auth; Max-Age=2592000")))
						.andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
						.andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
						.andExpect(jsonPath("$.data.accessToken").value("at-1"))
						.andExpect(jsonPath("$.data.refreshToken").doesNotExist());
	}

	@Test
	void registerShouldSetRefreshTokenCookie() throws Exception {
		LoginResponse result = LoginResponse.builder()
						.accessToken("at-2")
						.refreshToken("rt-2")
						.expiresIn(900L)
						.build();
		when(authService.register(any(), anyString())).thenReturn(result);

		mockMvc.perform(post("/auth/register")
										.contentType(MediaType.APPLICATION_JSON)
										.content("{\"username\":\"bob\",\"email\":\"bob@example.com\",\"password\":\"secret123\"}"))
						.andExpect(status().isOk())
						.andExpect(header().string("Set-Cookie", containsString("refresh_token=rt-2")));
	}

	@Test
	void refreshShouldRotateCookieAndReturnNewAccessToken() throws Exception {
		LoginResponse result = LoginResponse.builder()
						.accessToken("at-3")
						.refreshToken("rt-3")
						.expiresIn(900L)
						.build();
		when(authService.refresh("rt-old")).thenReturn(result);

		mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", "rt-old")))
						.andExpect(status().isOk())
						.andExpect(header().string("Set-Cookie", containsString("refresh_token=rt-3")))
						.andExpect(jsonPath("$.data.accessToken").value("at-3"));
	}

	@Test
	void refreshWithDisallowedOriginShouldReturnForbidden() throws Exception {
		jwtProperties.getAllowedOrigins().add("https://app.example.com");

		mockMvc.perform(post("/auth/refresh")
										.cookie(new Cookie("refresh_token", "rt-old"))
										.header("Origin", "https://evil.example.com"))
						.andExpect(status().isForbidden());
	}

	@Test
	void refreshWithSameOriginShouldBeAllowed() throws Exception {
		LoginResponse result = LoginResponse.builder().accessToken("at-4").refreshToken("rt-4").build();
		when(authService.refresh("rt-old")).thenReturn(result);

		mockMvc.perform(post("/auth/refresh")
										.cookie(new Cookie("refresh_token", "rt-old"))
										.header("Origin", "http://localhost"))
						.andExpect(status().isOk());
	}

	@Test
	void refreshWithoutCookieShouldPropagateSessionExpired() throws Exception {
		when(authService.refresh(null)).thenThrow(new vn.edu.epu.eoffice_be.common.exception.BusinessException(
						vn.edu.epu.eoffice_be.common.enums.ErrorCode.SESSION_EXPIRED));

		mockMvc.perform(post("/auth/refresh"))
						.andExpect(status().isUnauthorized())
						.andExpect(jsonPath("$.errorCode").value("SESSION_EXPIRED"));
	}

	@Test
	void logoutShouldClearCookieAndRevokeToken() throws Exception {
		mockMvc.perform(post("/auth/logout").cookie(new Cookie("refresh_token", "rt-old")))
						.andExpect(status().isOk())
						.andExpect(header().string("Set-Cookie", containsString("refresh_token=")))
						.andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

		verify(authService).logout("rt-old");
	}

	@Test
	void logoutWithDisallowedOriginShouldReturnForbidden() throws Exception {
		jwtProperties.getAllowedOrigins().add("https://app.example.com");

		mockMvc.perform(post("/auth/logout")
										.cookie(new Cookie("refresh_token", "rt-old"))
										.header("Origin", "https://evil.example.com"))
						.andExpect(status().isForbidden());
	}

	@Test
	void forgotPasswordShouldReturnGenericSuccess() throws Exception {
		mockMvc.perform(post("/auth/forgot-password")
										.contentType(MediaType.APPLICATION_JSON)
										.content("{\"email\":\"alice@example.com\"}"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.success").value(true));

		verify(passwordResetService).forgotPassword(any());
	}

	@Test
	void resetPasswordShouldReturnSuccess() throws Exception {
		mockMvc.perform(post("/auth/reset-password")
										.contentType(MediaType.APPLICATION_JSON)
										.content("{\"token\":\"raw-token\",\"newPassword\":\"NewPassword@2026\"}"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.success").value(true));

		verify(passwordResetService).resetPassword(any());
	}
}
