package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.PositionAssignmentCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.PositionAssignmentUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.PositionAssignmentResponse;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.PositionAssignment;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.OrgNodeRepository;
import vn.edu.epu.eoffice_be.repository.PositionAssignmentRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.service.impl.PositionAssignmentServiceImpl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PositionAssignmentServiceImplTest {

	@Mock
	PositionAssignmentRepository positionAssignmentRepository;

	@Mock
	UserLoginRepository userLoginRepository;

	@Mock
	OrgNodeRepository orgNodeRepository;

	@InjectMocks
	PositionAssignmentServiceImpl positionAssignmentService;

	private UserLogin user(Long id) {
		return UserLogin.builder()
						.id(id)
						.username("user" + id)
						.role(Role.USER)
						.build();
	}

	private OrgNode orgNode(Long id) {
		return OrgNode.builder()
						.id(id)
						.code("ORG" + id)
						.nameVn("Đơn vị " + id)
						.nameEn("Org " + id)
						.typeCd("DEPARTMENT")
						.build();
	}

	private PositionAssignment assignment(Long id, Long userId, Long orgId, String positionCd) {
		return PositionAssignment.builder()
						.id(id)
						.userLogin(user(userId))
						.orgNode(orgNode(orgId))
						.positionCd(positionCd)
						.effectiveFrom(Instant.parse("2026-01-01T00:00:00Z"))
						.effectiveTo(Instant.parse("2027-12-31T23:59:59Z"))
						.deleteFlg(false)
						.build();
	}

	// ---- listByUser (paginated) ----

	@Test
	void listByUserShouldReturnPagedAssignments() {
		Pageable pageable = PageRequest.of(0, 20);
		Page<PositionAssignment> page = new PageImpl<>(
						List.of(assignment(10L, 1L, 100L, "TP"), assignment(11L, 1L, 200L, "PP")),
						pageable, 2);
		when(positionAssignmentRepository.findByUserLoginIdAndDeleteFlgFalse(1L, pageable))
						.thenReturn(page);

		Page<PositionAssignmentResponse> result = positionAssignmentService.listByUser(1L, pageable);

		assertEquals(2, result.getContent().size());
		assertEquals("TP", result.getContent().get(0).getPositionCd());
		assertEquals("PP", result.getContent().get(1).getPositionCd());
		assertEquals("user1", result.getContent().get(0).getUsername());
	}

	@Test
	void listByUserShouldReturnEmptyPageWhenNoAssignments() {
		Pageable pageable = PageRequest.of(0, 20);
		Page<PositionAssignment> emptyPage = new PageImpl<>(List.of(), pageable, 0);
		when(positionAssignmentRepository.findByUserLoginIdAndDeleteFlgFalse(99L, pageable))
						.thenReturn(emptyPage);

		Page<PositionAssignmentResponse> result = positionAssignmentService.listByUser(99L, pageable);

		assertTrue(result.getContent().isEmpty());
		assertEquals(0, result.getTotalElements());
	}

	// ---- listByOrgNode (paginated) ----

	@Test
	void listByOrgNodeShouldReturnPagedAssignments() {
		Pageable pageable = PageRequest.of(0, 20);
		Page<PositionAssignment> page = new PageImpl<>(
						List.of(assignment(20L, 1L, 100L, "TP")),
						pageable, 1);
		when(positionAssignmentRepository.findByOrgNodeIdAndDeleteFlgFalse(100L, pageable))
						.thenReturn(page);

		Page<PositionAssignmentResponse> result = positionAssignmentService.listByOrgNode(100L, pageable);

		assertEquals(1, result.getContent().size());
		assertEquals(100L, result.getContent().get(0).getOrgNodeId());
		assertEquals("Đơn vị 100", result.getContent().get(0).getOrgNodeNameVn());
	}

	// ---- listActive (paginated) ----

	@Test
	void listActiveShouldReturnOnlyActiveAssignments() {
		Instant now = Instant.parse("2026-06-01T00:00:00Z");
		Pageable pageable = PageRequest.of(0, 20);
		Page<PositionAssignment> page = new PageImpl<>(
						List.of(assignment(30L, 1L, 100L, "TP")),
						pageable, 1);
		when(positionAssignmentRepository.findActiveAssignments(1L, now, pageable))
						.thenReturn(page);

		Page<PositionAssignmentResponse> result = positionAssignmentService.listActive(1L, now, pageable);

		assertEquals(1, result.getContent().size());
		assertEquals("TP", result.getContent().get(0).getPositionCd());
	}

	@Test
	void listActiveShouldDefaultToNowWhenAtTimeIsNull() {
		Pageable pageable = PageRequest.of(0, 20);
		Page<PositionAssignment> emptyPage = new PageImpl<>(List.of(), pageable, 0);
		when(positionAssignmentRepository.findActiveAssignments(eq(1L), any(Instant.class), eq(pageable)))
						.thenReturn(emptyPage);

		Page<PositionAssignmentResponse> result = positionAssignmentService.listActive(1L, null, pageable);

		assertNotNull(result);
		assertTrue(result.getContent().isEmpty());
		verify(positionAssignmentRepository).findActiveAssignments(eq(1L), any(Instant.class), eq(pageable));
	}

	// ---- getDetail ----

	@Test
	void getDetailShouldReturnAssignment() {
		when(positionAssignmentRepository.findById(10L))
						.thenReturn(Optional.of(assignment(10L, 1L, 100L, "TP")));

		PositionAssignmentResponse result = positionAssignmentService.getDetail(10L);

		assertNotNull(result);
		assertEquals(10L, result.getId());
		assertEquals("Trưởng phòng", result.getPositionName());
	}

	@Test
	void getDetailShouldThrowWhenNotFound() {
		when(positionAssignmentRepository.findById(999L))
						.thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
						() -> positionAssignmentService.getDetail(999L));

		assertEquals(ErrorCode.POSITION_ASSIGNMENT_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	void getDetailShouldThrowWhenSoftDeleted() {
		PositionAssignment deleted = assignment(10L, 1L, 100L, "TP");
		deleted.setDeleteFlg(true);
		when(positionAssignmentRepository.findById(10L))
						.thenReturn(Optional.of(deleted));

		BusinessException ex = assertThrows(BusinessException.class,
						() -> positionAssignmentService.getDetail(10L));

		assertEquals(ErrorCode.POSITION_ASSIGNMENT_NOT_FOUND, ex.getErrorCode());
	}

	// ---- create ----

	@Test
	void createShouldPersistNewAssignment() {
		PositionAssignmentCreateRequest request = new PositionAssignmentCreateRequest();
		request.setUserLoginId(1L);
		request.setOrgNodeId(100L);
		request.setPositionCd("TP");
		request.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
		request.setEffectiveTo(Instant.parse("2027-12-31T23:59:59Z"));

		when(userLoginRepository.findByIdAndDeleteFlgFalse(1L))
						.thenReturn(Optional.of(user(1L)));
		when(orgNodeRepository.findByIdAndDeleteFlgFalse(100L))
						.thenReturn(Optional.of(orgNode(100L)));
		when(positionAssignmentRepository.existsOverlappingAssignment(eq(1L), any(), any(), any()))
						.thenReturn(false);
		when(positionAssignmentRepository.save(any(PositionAssignment.class)))
						.thenAnswer(inv -> {
							PositionAssignment pa = inv.getArgument(0);
							pa.setId(50L);
							return pa;
						});

		PositionAssignmentResponse result = positionAssignmentService.create(request);

		assertNotNull(result);
		assertEquals(50L, result.getId());
		assertEquals("TP", result.getPositionCd());
		assertEquals("Trưởng phòng", result.getPositionName());
		verify(positionAssignmentRepository).save(any(PositionAssignment.class));
	}

	@Test
	void createShouldDefaultEffectiveFromToNowWhenNull() {
		PositionAssignmentCreateRequest request = new PositionAssignmentCreateRequest();
		request.setUserLoginId(1L);
		request.setOrgNodeId(100L);
		request.setPositionCd("CV");
		request.setEffectiveFrom(null);
		request.setEffectiveTo(null);

		when(userLoginRepository.findByIdAndDeleteFlgFalse(1L))
						.thenReturn(Optional.of(user(1L)));
		when(orgNodeRepository.findByIdAndDeleteFlgFalse(100L))
						.thenReturn(Optional.of(orgNode(100L)));
		when(positionAssignmentRepository.existsOverlappingAssignment(eq(1L), any(), any(), any()))
						.thenReturn(false);
		when(positionAssignmentRepository.save(any(PositionAssignment.class)))
						.thenAnswer(inv -> {
							PositionAssignment pa = inv.getArgument(0);
							pa.setId(51L);
							return pa;
						});

		PositionAssignmentResponse result = positionAssignmentService.create(request);

		assertNotNull(result);
		assertNotNull(result.getEffectiveFrom());
	}

	@Test
	void createShouldThrowWhenPositionCdInvalid() {
		PositionAssignmentCreateRequest request = new PositionAssignmentCreateRequest();
		request.setUserLoginId(1L);
		request.setOrgNodeId(100L);
		request.setPositionCd("INVALID");

		BusinessException ex = assertThrows(BusinessException.class,
						() -> positionAssignmentService.create(request));

		assertEquals(ErrorCode.POSITION_CD_INVALID, ex.getErrorCode());
	}

	@Test
	void createShouldThrowWhenUserNotFound() {
		PositionAssignmentCreateRequest request = new PositionAssignmentCreateRequest();
		request.setUserLoginId(999L);
		request.setOrgNodeId(100L);
		request.setPositionCd("TP");

		when(userLoginRepository.findByIdAndDeleteFlgFalse(999L))
						.thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
						() -> positionAssignmentService.create(request));

		assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
	}

	@Test
	void createShouldThrowWhenOrgNodeNotFound() {
		PositionAssignmentCreateRequest request = new PositionAssignmentCreateRequest();
		request.setUserLoginId(1L);
		request.setOrgNodeId(999L);
		request.setPositionCd("TP");

		when(userLoginRepository.findByIdAndDeleteFlgFalse(1L))
						.thenReturn(Optional.of(user(1L)));
		when(orgNodeRepository.findByIdAndDeleteFlgFalse(999L))
						.thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
						() -> positionAssignmentService.create(request));

		assertEquals(ErrorCode.ORG_NODE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	void createShouldThrowWhenEffectiveToBeforeEffectiveFrom() {
		PositionAssignmentCreateRequest request = new PositionAssignmentCreateRequest();
		request.setUserLoginId(1L);
		request.setOrgNodeId(100L);
		request.setPositionCd("TP");
		request.setEffectiveFrom(Instant.parse("2027-01-01T00:00:00Z"));
		request.setEffectiveTo(Instant.parse("2026-01-01T00:00:00Z"));

		when(userLoginRepository.findByIdAndDeleteFlgFalse(1L))
						.thenReturn(Optional.of(user(1L)));
		when(orgNodeRepository.findByIdAndDeleteFlgFalse(100L))
						.thenReturn(Optional.of(orgNode(100L)));

		BusinessException ex = assertThrows(BusinessException.class,
						() -> positionAssignmentService.create(request));

		assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
		verify(positionAssignmentRepository, never()).save(any(PositionAssignment.class));
	}

	@Test
	void createShouldThrowWhenOverlappingAssignmentExists() {
		PositionAssignmentCreateRequest request = new PositionAssignmentCreateRequest();
		request.setUserLoginId(1L);
		request.setOrgNodeId(100L);
		request.setPositionCd("TP");
		request.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
		request.setEffectiveTo(Instant.parse("2027-12-31T23:59:59Z"));

		when(userLoginRepository.findByIdAndDeleteFlgFalse(1L))
						.thenReturn(Optional.of(user(1L)));
		when(orgNodeRepository.findByIdAndDeleteFlgFalse(100L))
						.thenReturn(Optional.of(orgNode(100L)));
		when(positionAssignmentRepository.existsOverlappingAssignment(eq(1L), any(), any(), any()))
						.thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
						() -> positionAssignmentService.create(request));

		assertEquals(ErrorCode.POSITION_ASSIGNMENT_OVERLAP, ex.getErrorCode());
	}

	// ---- update ----

	@Test
	void updateShouldExtendEffectiveTo() {
		PositionAssignmentUpdateRequest request = new PositionAssignmentUpdateRequest();
		request.setEffectiveTo(Instant.parse("2028-12-31T23:59:59Z"));

		PositionAssignment existing = assignment(10L, 1L, 100L, "TP");
		when(positionAssignmentRepository.findById(10L))
						.thenReturn(Optional.of(existing));
		when(positionAssignmentRepository.existsOverlappingAssignment(eq(1L), any(), any(), eq(10L)))
						.thenReturn(false);
		when(positionAssignmentRepository.save(any(PositionAssignment.class)))
						.thenAnswer(inv -> inv.getArgument(0));

		PositionAssignmentResponse result = positionAssignmentService.update(10L, request);

		assertEquals(Instant.parse("2028-12-31T23:59:59Z"), result.getEffectiveTo());
		verify(positionAssignmentRepository).existsOverlappingAssignment(eq(1L), any(), any(), eq(10L));
	}

	@Test
	void updateShouldThrowWhenExtendingToOverlapExistingAssignment() {
		PositionAssignmentUpdateRequest request = new PositionAssignmentUpdateRequest();
		request.setEffectiveTo(Instant.parse("2028-12-31T23:59:59Z"));

		PositionAssignment existing = assignment(10L, 1L, 100L, "TP");
		when(positionAssignmentRepository.findById(10L))
						.thenReturn(Optional.of(existing));
		when(positionAssignmentRepository.existsOverlappingAssignment(eq(1L), any(), any(), eq(10L)))
						.thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
						() -> positionAssignmentService.update(10L, request));

		assertEquals(ErrorCode.POSITION_ASSIGNMENT_OVERLAP, ex.getErrorCode());
		verify(positionAssignmentRepository, never()).save(any());
	}

	@Test
	void updateShouldNotCheckOverlapWhenEffectiveToUnchanged() {
		PositionAssignmentUpdateRequest request = new PositionAssignmentUpdateRequest();
		request.setActualDismissalDate(Instant.parse("2026-06-30T00:00:00Z"));

		PositionAssignment existing = assignment(10L, 1L, 100L, "TP");
		when(positionAssignmentRepository.findById(10L))
						.thenReturn(Optional.of(existing));
		when(positionAssignmentRepository.save(any(PositionAssignment.class)))
						.thenAnswer(inv -> inv.getArgument(0));

		positionAssignmentService.update(10L, request);

		verify(positionAssignmentRepository, never()).existsOverlappingAssignment(any(), any(), any(), any());
	}

	@Test
	void updateShouldThrowWhenNewEffectiveToBeforeExistingEffectiveFrom() {
		PositionAssignmentUpdateRequest request = new PositionAssignmentUpdateRequest();
		request.setEffectiveTo(Instant.parse("2025-01-01T00:00:00Z"));

		PositionAssignment existing = assignment(10L, 1L, 100L, "TP");
		existing.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
		when(positionAssignmentRepository.findById(10L))
						.thenReturn(Optional.of(existing));

		BusinessException ex = assertThrows(BusinessException.class,
						() -> positionAssignmentService.update(10L, request));

		assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
	}

	@Test
	void updateShouldThrowWhenAssignmentNotFound() {
		PositionAssignmentUpdateRequest request = new PositionAssignmentUpdateRequest();
		request.setEffectiveTo(Instant.parse("2028-12-31T23:59:59Z"));

		when(positionAssignmentRepository.findById(999L))
						.thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
						() -> positionAssignmentService.update(999L, request));

		assertEquals(ErrorCode.POSITION_ASSIGNMENT_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	void updateShouldThrowWhenDismissalDateBeforeEffectiveFrom() {
		PositionAssignmentUpdateRequest request = new PositionAssignmentUpdateRequest();
		request.setActualDismissalDate(Instant.parse("2025-06-30T00:00:00Z"));

		PositionAssignment existing = assignment(10L, 1L, 100L, "TP");
		existing.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
		when(positionAssignmentRepository.findById(10L))
						.thenReturn(Optional.of(existing));

		BusinessException ex = assertThrows(BusinessException.class,
						() -> positionAssignmentService.update(10L, request));

		assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
		verify(positionAssignmentRepository, never()).save(any());
	}

	@Test
	void updateShouldThrowWhenDismissalDateAfterEffectiveTo() {
		PositionAssignmentUpdateRequest request = new PositionAssignmentUpdateRequest();
		request.setActualDismissalDate(Instant.parse("2028-06-30T00:00:00Z"));

		PositionAssignment existing = assignment(10L, 1L, 100L, "TP");
		existing.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
		existing.setEffectiveTo(Instant.parse("2027-12-31T23:59:59Z"));
		when(positionAssignmentRepository.findById(10L))
						.thenReturn(Optional.of(existing));

		BusinessException ex = assertThrows(BusinessException.class,
						() -> positionAssignmentService.update(10L, request));

		assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
		verify(positionAssignmentRepository, never()).save(any());
	}

	// ---- dismiss ----

	@Test
	void dismissShouldSetActualDismissalDate() {
		PositionAssignmentUpdateRequest request = new PositionAssignmentUpdateRequest();
		request.setActualDismissalDate(Instant.parse("2026-06-30T00:00:00Z"));
		request.setDismissalReasonCd("TRANSFER");
		request.setDismissalNote("Chuyển đơn vị");

		PositionAssignment existing = assignment(10L, 1L, 100L, "TP");
		when(positionAssignmentRepository.findById(10L))
						.thenReturn(Optional.of(existing));
		when(positionAssignmentRepository.save(any(PositionAssignment.class)))
						.thenAnswer(inv -> inv.getArgument(0));

		positionAssignmentService.dismiss(10L, request);

		assertEquals(Instant.parse("2026-06-30T00:00:00Z"), existing.getActualDismissalDate());
		assertEquals("TRANSFER", existing.getDismissalReasonCd());
		assertEquals("Chuyển đơn vị", existing.getDismissalNote());
	}

	@Test
	void dismissShouldDefaultToNowWhenDismissalDateNull() {
		PositionAssignmentUpdateRequest request = new PositionAssignmentUpdateRequest();
		request.setActualDismissalDate(null);
		request.setDismissalReasonCd("RESIGN");

		PositionAssignment existing = assignment(10L, 1L, 100L, "TP");
		when(positionAssignmentRepository.findById(10L))
						.thenReturn(Optional.of(existing));
		when(positionAssignmentRepository.save(any(PositionAssignment.class)))
						.thenAnswer(inv -> inv.getArgument(0));

		positionAssignmentService.dismiss(10L, request);

		assertNotNull(existing.getActualDismissalDate());
		assertEquals("RESIGN", existing.getDismissalReasonCd());
	}

	@Test
	void dismissShouldThrowWhenAlreadyDismissed() {
		PositionAssignmentUpdateRequest request = new PositionAssignmentUpdateRequest();
		request.setActualDismissalDate(Instant.parse("2026-06-30T00:00:00Z"));

		PositionAssignment existing = assignment(10L, 1L, 100L, "TP");
		existing.setActualDismissalDate(Instant.parse("2026-01-15T00:00:00Z"));
		when(positionAssignmentRepository.findById(10L))
						.thenReturn(Optional.of(existing));

		BusinessException ex = assertThrows(BusinessException.class,
						() -> positionAssignmentService.dismiss(10L, request));

		assertEquals(ErrorCode.POSITION_ASSIGNMENT_ALREADY_DISMISSED, ex.getErrorCode());
	}

	@Test
	void dismissShouldThrowWhenDismissalDateBeforeEffectiveFrom() {
		PositionAssignmentUpdateRequest request = new PositionAssignmentUpdateRequest();
		request.setActualDismissalDate(Instant.parse("2025-06-30T00:00:00Z"));

		PositionAssignment existing = assignment(10L, 1L, 100L, "TP");
		existing.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
		when(positionAssignmentRepository.findById(10L))
						.thenReturn(Optional.of(existing));

		BusinessException ex = assertThrows(BusinessException.class,
						() -> positionAssignmentService.dismiss(10L, request));

		assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
		verify(positionAssignmentRepository, never()).save(any());
	}

	@Test
	void dismissShouldThrowWhenDismissalDateAfterEffectiveTo() {
		PositionAssignmentUpdateRequest request = new PositionAssignmentUpdateRequest();
		request.setActualDismissalDate(Instant.parse("2028-06-30T00:00:00Z"));

		PositionAssignment existing = assignment(10L, 1L, 100L, "TP");
		existing.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
		existing.setEffectiveTo(Instant.parse("2027-12-31T23:59:59Z"));
		when(positionAssignmentRepository.findById(10L))
						.thenReturn(Optional.of(existing));

		BusinessException ex = assertThrows(BusinessException.class,
						() -> positionAssignmentService.dismiss(10L, request));

		assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
		verify(positionAssignmentRepository, never()).save(any());
	}

	// ---- delete ----

	@Test
	void deleteShouldSoftDelete() {
		PositionAssignment existing = assignment(10L, 1L, 100L, "TP");
		when(positionAssignmentRepository.findById(10L))
						.thenReturn(Optional.of(existing));
		when(positionAssignmentRepository.save(any(PositionAssignment.class)))
						.thenAnswer(inv -> inv.getArgument(0));

		positionAssignmentService.delete(10L);

		assertTrue(existing.isDeleteFlg());
	}

	@Test
	void deleteShouldThrowWhenNotFound() {
		when(positionAssignmentRepository.findById(999L))
						.thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
						() -> positionAssignmentService.delete(999L));

		assertEquals(ErrorCode.POSITION_ASSIGNMENT_NOT_FOUND, ex.getErrorCode());
	}

	// ---- resolvePositionName ----

	@Test
	void resolvePositionNameShouldMapAllValidCodes() {
		assertEquals("Hiệu trưởng", positionAssignmentService.create(buildCreateRequest("HT")).getPositionName());
		assertEquals("Phó Hiệu trưởng", positionAssignmentService.create(buildCreateRequest("PHT")).getPositionName());
		assertEquals("Trưởng khoa", positionAssignmentService.create(buildCreateRequest("TK")).getPositionName());
		assertEquals("Phó Trưởng khoa", positionAssignmentService.create(buildCreateRequest("PK")).getPositionName());
		assertEquals("Trưởng phòng", positionAssignmentService.create(buildCreateRequest("TP")).getPositionName());
		assertEquals("Phó Trưởng phòng", positionAssignmentService.create(buildCreateRequest("PP")).getPositionName());
		assertEquals("Chuyên viên", positionAssignmentService.create(buildCreateRequest("CV")).getPositionName());
	}

	private PositionAssignmentCreateRequest buildCreateRequest(String positionCd) {
		PositionAssignmentCreateRequest request = new PositionAssignmentCreateRequest();
		request.setUserLoginId(1L);
		request.setOrgNodeId(100L);
		request.setPositionCd(positionCd);
		request.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
		request.setEffectiveTo(Instant.parse("2027-12-31T23:59:59Z"));

		when(userLoginRepository.findByIdAndDeleteFlgFalse(1L))
						.thenReturn(Optional.of(user(1L)));
		when(orgNodeRepository.findByIdAndDeleteFlgFalse(100L))
						.thenReturn(Optional.of(orgNode(100L)));
		when(positionAssignmentRepository.existsOverlappingAssignment(eq(1L), any(), any(), any()))
						.thenReturn(false);
		when(positionAssignmentRepository.save(any(PositionAssignment.class)))
						.thenAnswer(inv -> {
							PositionAssignment pa = inv.getArgument(0);
							pa.setId(50L);
							return pa;
						});

		return request;
	}
}
