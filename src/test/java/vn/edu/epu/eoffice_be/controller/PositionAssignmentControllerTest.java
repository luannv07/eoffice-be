package vn.edu.epu.eoffice_be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.common.exception.GlobalExceptionHandler;
import vn.edu.epu.eoffice_be.config.JacksonConfig;
import vn.edu.epu.eoffice_be.dto.request.PositionAssignmentCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.PositionAssignmentUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.PositionAssignmentResponse;
import vn.edu.epu.eoffice_be.service.PositionAssignmentService;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PositionAssignmentControllerTest {

	@Mock
	PositionAssignmentService positionAssignmentService;

	MockMvc mockMvc;

	ObjectMapper objectMapper;

	@BeforeEach
	void setup() {
		objectMapper = new JacksonConfig().objectMapper();
		mockMvc = MockMvcBuilders.standaloneSetup(new PositionAssignmentController(positionAssignmentService))
						.setControllerAdvice(new GlobalExceptionHandler())
						.setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
						.setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
						.build();
	}

	private PositionAssignmentResponse assignment(Long id, Long userId, Long orgId, String positionCd) {
		return PositionAssignmentResponse.builder()
						.id(id)
						.userLoginId(userId)
						.username("user" + userId)
						.orgNodeId(orgId)
						.orgNodeNameVn("Đơn vị " + orgId)
						.positionCd(positionCd)
						.positionName("Trưởng phòng")
						.effectiveFrom(Instant.parse("2026-01-01T00:00:00Z"))
						.effectiveTo(Instant.parse("2027-12-31T23:59:59Z"))
						.deleteFlg(false)
						.build();
	}

	// ---- listByUser ----

	@Test
	void listByUserShouldReturnPagedList() throws Exception {
		Pageable pageable = PageRequest.of(0, 20);
		Page<PositionAssignmentResponse> page = new PageImpl<>(
						List.of(assignment(10L, 1L, 100L, "TP")),
						pageable, 1);
		when(positionAssignmentService.listByUser(1L, pageable))
						.thenReturn(page);

		mockMvc.perform(get("/position-assignments/user/1"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.success").value(true))
						.andExpect(jsonPath("$.data.content.length()").value(1))
						.andExpect(jsonPath("$.data.content[0].positionCd").value("TP"));

		verify(positionAssignmentService).listByUser(eq(1L), any(Pageable.class));
	}

	@Test
	void listByUserShouldReturnEmptyPageWhenNoData() throws Exception {
		Pageable pageable = PageRequest.of(0, 20);
		Page<PositionAssignmentResponse> emptyPage = new PageImpl<>(List.of(), pageable, 0);
		when(positionAssignmentService.listByUser(99L, pageable))
						.thenReturn(emptyPage);

		mockMvc.perform(get("/position-assignments/user/99"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.data.content.length()").value(0))
						.andExpect(jsonPath("$.data.totalElements").value(0));
	}

	// ---- listByOrgNode ----

	@Test
	void listByOrgNodeShouldReturnPagedList() throws Exception {
		Pageable pageable = PageRequest.of(0, 20);
		Page<PositionAssignmentResponse> page = new PageImpl<>(
						List.of(assignment(10L, 1L, 100L, "TP")),
						pageable, 1);
		when(positionAssignmentService.listByOrgNode(100L, pageable))
						.thenReturn(page);

		mockMvc.perform(get("/position-assignments/org-node/100"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.data.content[0].orgNodeId").value(100));

		verify(positionAssignmentService).listByOrgNode(eq(100L), any(Pageable.class));
	}

	// ---- listActive ----

	@Test
	void listActiveShouldReturnActiveAssignments() throws Exception {
		Pageable pageable = PageRequest.of(0, 20);
		Page<PositionAssignmentResponse> page = new PageImpl<>(
						List.of(assignment(10L, 1L, 100L, "TP")),
						pageable, 1);
		when(positionAssignmentService.listActive(eq(1L), any(Instant.class), eq(pageable)))
						.thenReturn(page);

		mockMvc.perform(get("/position-assignments/user/1/active")
										.param("atTime", "2026-06-01T00:00:00Z"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.data.content.length()").value(1));

		verify(positionAssignmentService).listActive(eq(1L), any(Instant.class), eq(pageable));
	}

	@Test
	void listActiveShouldWorkWithoutAtTime() throws Exception {
		Pageable pageable = PageRequest.of(0, 20);
		Page<PositionAssignmentResponse> emptyPage = new PageImpl<>(List.of(), pageable, 0);
		when(positionAssignmentService.listActive(eq(1L), any(), eq(pageable)))
						.thenReturn(emptyPage);

		mockMvc.perform(get("/position-assignments/user/1/active"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.data.content.length()").value(0));
	}

	// ---- getDetail ----

	@Test
	void getDetailShouldReturnAssignment() throws Exception {
		when(positionAssignmentService.getDetail(10L))
						.thenReturn(assignment(10L, 1L, 100L, "TP"));

		mockMvc.perform(get("/position-assignments/10"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.data.id").value(10))
						.andExpect(jsonPath("$.data.positionCd").value("TP"));

		verify(positionAssignmentService).getDetail(10L);
	}

	@Test
	void getDetailShouldReturn404WhenNotFound() throws Exception {
		when(positionAssignmentService.getDetail(999L))
						.thenThrow(new BusinessException(vn.edu.epu.eoffice_be.common.enums.ErrorCode.POSITION_ASSIGNMENT_NOT_FOUND));

		mockMvc.perform(get("/position-assignments/999"))
						.andExpect(status().isNotFound());
	}

	// ---- create ----

	@Test
	void createShouldReturnCreatedAssignment() throws Exception {
		PositionAssignmentCreateRequest request = new PositionAssignmentCreateRequest();
		request.setUserLoginId(1L);
		request.setOrgNodeId(100L);
		request.setPositionCd("TP");
		request.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
		request.setEffectiveTo(Instant.parse("2027-12-31T23:59:59Z"));

		when(positionAssignmentService.create(any(PositionAssignmentCreateRequest.class)))
						.thenReturn(assignment(10L, 1L, 100L, "TP"));

		mockMvc.perform(post("/position-assignments")
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(request)))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.success").value(true))
						.andExpect(jsonPath("$.data.id").value(10));

		verify(positionAssignmentService).create(any(PositionAssignmentCreateRequest.class));
	}

	@Test
	void createShouldReturn400WhenPositionCdInvalid() throws Exception {
		PositionAssignmentCreateRequest request = new PositionAssignmentCreateRequest();
		request.setUserLoginId(1L);
		request.setOrgNodeId(100L);
		request.setPositionCd("INVALID");
		request.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
		request.setEffectiveTo(Instant.parse("2027-12-31T23:59:59Z"));

		when(positionAssignmentService.create(any(PositionAssignmentCreateRequest.class)))
						.thenThrow(new BusinessException(vn.edu.epu.eoffice_be.common.enums.ErrorCode.POSITION_CD_INVALID));

		mockMvc.perform(post("/position-assignments")
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(request)))
						.andExpect(status().isBadRequest());
	}

	@Test
	void createShouldReturn409WhenOverlapping() throws Exception {
		PositionAssignmentCreateRequest request = new PositionAssignmentCreateRequest();
		request.setUserLoginId(1L);
		request.setOrgNodeId(100L);
		request.setPositionCd("TP");
		request.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
		request.setEffectiveTo(Instant.parse("2027-12-31T23:59:59Z"));

		when(positionAssignmentService.create(any(PositionAssignmentCreateRequest.class)))
						.thenThrow(new BusinessException(vn.edu.epu.eoffice_be.common.enums.ErrorCode.POSITION_ASSIGNMENT_OVERLAP));

		mockMvc.perform(post("/position-assignments")
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(request)))
						.andExpect(status().isConflict());
	}

	@Test
	void createShouldReturn400WhenValidationFails() throws Exception {
		PositionAssignmentCreateRequest request = new PositionAssignmentCreateRequest();
		request.setUserLoginId(null);
		request.setOrgNodeId(null);
		request.setPositionCd("");

		mockMvc.perform(post("/position-assignments")
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(request)))
						.andExpect(status().isBadRequest());
	}

	// ---- update ----

	@Test
	void updateShouldReturnUpdatedAssignment() throws Exception {
		PositionAssignmentUpdateRequest request = new PositionAssignmentUpdateRequest();
		request.setEffectiveTo(Instant.parse("2028-12-31T23:59:59Z"));

		when(positionAssignmentService.update(eq(10L), any(PositionAssignmentUpdateRequest.class)))
						.thenReturn(assignment(10L, 1L, 100L, "TP"));

		mockMvc.perform(put("/position-assignments/10")
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(request)))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.data.id").value(10));

		verify(positionAssignmentService).update(eq(10L), any(PositionAssignmentUpdateRequest.class));
	}

	@Test
	void updateShouldReturn404WhenNotFound() throws Exception {
		PositionAssignmentUpdateRequest request = new PositionAssignmentUpdateRequest();
		request.setEffectiveTo(Instant.parse("2028-12-31T23:59:59Z"));

		when(positionAssignmentService.update(eq(999L), any(PositionAssignmentUpdateRequest.class)))
						.thenThrow(new BusinessException(vn.edu.epu.eoffice_be.common.enums.ErrorCode.POSITION_ASSIGNMENT_NOT_FOUND));

		mockMvc.perform(put("/position-assignments/999")
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(request)))
						.andExpect(status().isNotFound());
	}

	@Test
	void updateShouldReturn409WhenOverlapAfterExtension() throws Exception {
		PositionAssignmentUpdateRequest request = new PositionAssignmentUpdateRequest();
		request.setEffectiveTo(Instant.parse("2028-12-31T23:59:59Z"));

		when(positionAssignmentService.update(eq(10L), any(PositionAssignmentUpdateRequest.class)))
						.thenThrow(new BusinessException(vn.edu.epu.eoffice_be.common.enums.ErrorCode.POSITION_ASSIGNMENT_OVERLAP));

		mockMvc.perform(put("/position-assignments/10")
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(request)))
						.andExpect(status().isConflict());
	}

	@Test
	void updateShouldReturn400WhenDismissalDateBeforeEffectiveFrom() throws Exception {
		PositionAssignmentUpdateRequest request = new PositionAssignmentUpdateRequest();
		request.setActualDismissalDate(Instant.parse("2025-06-30T00:00:00Z"));

		when(positionAssignmentService.update(eq(10L), any(PositionAssignmentUpdateRequest.class)))
						.thenThrow(new BusinessException(vn.edu.epu.eoffice_be.common.enums.ErrorCode.BAD_REQUEST));

		mockMvc.perform(put("/position-assignments/10")
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(request)))
						.andExpect(status().isBadRequest());
	}

	// ---- dismiss ----

	@Test
	void dismissShouldReturn200() throws Exception {
		PositionAssignmentUpdateRequest request = new PositionAssignmentUpdateRequest();
		request.setActualDismissalDate(Instant.parse("2026-06-30T00:00:00Z"));
		request.setDismissalReasonCd("TRANSFER");

		doNothing().when(positionAssignmentService).dismiss(eq(10L), any(PositionAssignmentUpdateRequest.class));

		mockMvc.perform(post("/position-assignments/10/dismiss")
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(request)))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.success").value(true));

		verify(positionAssignmentService).dismiss(eq(10L), any(PositionAssignmentUpdateRequest.class));
	}

	@Test
	void dismissShouldReturn400WhenAlreadyDismissed() throws Exception {
		PositionAssignmentUpdateRequest request = new PositionAssignmentUpdateRequest();
		request.setActualDismissalDate(Instant.parse("2026-06-30T00:00:00Z"));

		doThrow(new BusinessException(vn.edu.epu.eoffice_be.common.enums.ErrorCode.POSITION_ASSIGNMENT_ALREADY_DISMISSED))
						.when(positionAssignmentService).dismiss(eq(10L), any(PositionAssignmentUpdateRequest.class));

		mockMvc.perform(post("/position-assignments/10/dismiss")
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(request)))
						.andExpect(status().isBadRequest());
	}

	@Test
	void dismissShouldReturn400WhenDismissalDateInvalid() throws Exception {
		PositionAssignmentUpdateRequest request = new PositionAssignmentUpdateRequest();
		request.setActualDismissalDate(Instant.parse("2025-06-30T00:00:00Z"));

		doThrow(new BusinessException(vn.edu.epu.eoffice_be.common.enums.ErrorCode.BAD_REQUEST))
						.when(positionAssignmentService).dismiss(eq(10L), any(PositionAssignmentUpdateRequest.class));

		mockMvc.perform(post("/position-assignments/10/dismiss")
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(request)))
						.andExpect(status().isBadRequest());
	}

	// ---- delete ----

	@Test
	void deleteShouldReturn200() throws Exception {
		doNothing().when(positionAssignmentService).delete(10L);

		mockMvc.perform(delete("/position-assignments/10"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.success").value(true));

		verify(positionAssignmentService).delete(10L);
	}

	@Test
	void deleteShouldReturn404WhenNotFound() throws Exception {
		doThrow(new BusinessException(vn.edu.epu.eoffice_be.common.enums.ErrorCode.POSITION_ASSIGNMENT_NOT_FOUND))
						.when(positionAssignmentService).delete(999L);

		mockMvc.perform(delete("/position-assignments/999"))
						.andExpect(status().isNotFound());
	}
}
