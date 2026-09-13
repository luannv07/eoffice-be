package vn.edu.epu.eoffice_be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.epu.eoffice_be.common.exception.GlobalExceptionHandler;
import vn.edu.epu.eoffice_be.dto.response.UserDetailResponse;
import vn.edu.epu.eoffice_be.dto.response.UserSummaryResponse;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import org.springframework.data.web.config.SpringDataJacksonConfiguration;
import vn.edu.epu.eoffice_be.service.UserManagementService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

	@Mock
	UserManagementService userManagementService;

	@InjectMocks
	AdminUserController adminUserController;

	MockMvc mockMvc;
	ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper().findAndRegisterModules()
						.registerModule(new SpringDataJacksonConfiguration().pageModule());
		mockMvc = MockMvcBuilders.standaloneSetup(adminUserController)
						.setControllerAdvice(new GlobalExceptionHandler())
						.setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
						.build();
		JwtUserPrincipal principal = new JwtUserPrincipal(1L, "admin", "SYSTEM_ADMIN", true, List.of());
		SecurityContextHolder.getContext()
						.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, "n/a", List.of()));
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void listShouldDelegateToService() throws Exception {
		when(userManagementService.list(any(), any(Pageable.class)))
						.thenReturn(new PageImpl<>(List.of(UserSummaryResponse.builder().id(1L).username("alice").build())));
		mockMvc.perform(get("/admin/users"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.data.content[0].username").value("alice"));
		verify(userManagementService).list(any(), any(Pageable.class));
	}

	@Test
	void getDetailShouldDelegateToService() throws Exception {
		when(userManagementService.getDetail(1L))
						.thenReturn(UserDetailResponse.builder().id(1L).username("alice").build());
		mockMvc.perform(get("/admin/users/1"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.data.username").value("alice"));
		verify(userManagementService).getDetail(1L);
	}

	@Test
	void createShouldDelegateToService() throws Exception {
		when(userManagementService.create(any())).thenReturn(UserDetailResponse.builder().id(2L).build());
		mockMvc.perform(post("/admin/users")
										.contentType(MediaType.APPLICATION_JSON)
										.content("{\"username\":\"bob\",\"email\":\"bob@epu.edu.vn\",\"password\":\"MatKhau@2026\"}"))
						.andExpect(status().isOk());
		verify(userManagementService).create(any());
	}

	@Test
	void createShouldReturnValidationErrorOnBlankUsername() throws Exception {
		mockMvc.perform(post("/admin/users")
										.contentType(MediaType.APPLICATION_JSON)
										.content("{\"username\":\"\",\"email\":\"bob@epu.edu.vn\",\"password\":\"MatKhau@2026\"}"))
						.andExpect(status().isBadRequest());
		verify(userManagementService, org.mockito.Mockito.never()).create(any());
	}

	@Test
	void updateShouldDelegateToService() throws Exception {
		when(userManagementService.update(eq(3L), any())).thenReturn(UserDetailResponse.builder().id(3L).build());
		mockMvc.perform(put("/admin/users/3")
										.contentType(MediaType.APPLICATION_JSON)
										.content("{\"mustChangePassword\":true}"))
						.andExpect(status().isOk());
		verify(userManagementService).update(eq(3L), any());
	}

	@Test
	void deleteShouldDelegateToService() throws Exception {
		mockMvc.perform(delete("/admin/users/4")).andExpect(status().isOk());
		verify(userManagementService).delete(4L);
	}

	@Test
	void restoreShouldDelegateToService() throws Exception {
		mockMvc.perform(post("/admin/users/5/restore")).andExpect(status().isOk());
		verify(userManagementService).restore(5L);
	}

	@Test
	void lockShouldDelegateToService() throws Exception {
		mockMvc.perform(post("/admin/users/6/lock")).andExpect(status().isOk());
		verify(userManagementService).lock(6L);
	}

	@Test
	void unlockShouldDelegateToService() throws Exception {
		mockMvc.perform(post("/admin/users/6/unlock")).andExpect(status().isOk());
		verify(userManagementService).unlock(6L);
	}

	@Test
	void banShouldDelegateToService() throws Exception {
		mockMvc.perform(post("/admin/users/7/ban")).andExpect(status().isOk());
		verify(userManagementService).ban(7L);
	}

	@Test
	void activateShouldDelegateToService() throws Exception {
		mockMvc.perform(post("/admin/users/7/activate")).andExpect(status().isOk());
		verify(userManagementService).activate(7L);
	}

	@Test
	void changeRoleShouldDelegateToService() throws Exception {
		when(userManagementService.changeRole(eq(8L), any())).thenReturn(UserDetailResponse.builder().id(8L).build());
		mockMvc.perform(post("/admin/users/8/role")
										.contentType(MediaType.APPLICATION_JSON)
										.content("{\"role\":\"ADMIN\"}"))
						.andExpect(status().isOk());
		verify(userManagementService).changeRole(eq(8L), any());
	}
}
