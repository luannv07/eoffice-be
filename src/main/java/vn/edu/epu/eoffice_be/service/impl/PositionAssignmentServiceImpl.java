package vn.edu.epu.eoffice_be.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
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
import vn.edu.epu.eoffice_be.service.PositionAssignmentService;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PositionAssignmentServiceImpl implements PositionAssignmentService {

	private static final Set<String> VALID_POSITION_CDS = Set.of("HT", "PHT", "TK", "PK", "TP", "PP", "CV");

	PositionAssignmentRepository positionAssignmentRepository;
	UserLoginRepository userLoginRepository;
	OrgNodeRepository orgNodeRepository;

	@Override
	@Transactional(readOnly = true)
	public List<PositionAssignmentResponse> listByUser(Long userLoginId) {
		return positionAssignmentRepository.findByUserLoginIdAndDeleteFlgFalse(userLoginId)
						.stream()
						.map(this::toResponse)
						.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<PositionAssignmentResponse> listByOrgNode(Long orgNodeId) {
		return positionAssignmentRepository.findByOrgNodeIdAndDeleteFlgFalse(orgNodeId)
						.stream()
						.map(this::toResponse)
						.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<PositionAssignmentResponse> listActive(Long userId, Instant atTime) {
		Instant effectiveAt = atTime != null ? atTime : Instant.now();
		return positionAssignmentRepository.findActiveAssignments(userId, effectiveAt)
						.stream()
						.map(this::toResponse)
						.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public PositionAssignmentResponse getDetail(Long id) {
		return toResponse(findById(id));
	}

	@Override
	@Transactional
	public PositionAssignmentResponse create(PositionAssignmentCreateRequest request) {
		validatePositionCd(request.getPositionCd());

		UserLogin user = userLoginRepository.findByIdAndDeleteFlgFalse(request.getUserLoginId())
						.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy tài khoản với id=" + request.getUserLoginId()));

		OrgNode orgNode = orgNodeRepository.findByIdAndDeleteFlgFalse(request.getOrgNodeId())
						.orElseThrow(() -> new BusinessException(ErrorCode.ORG_NODE_NOT_FOUND));

		Instant effectiveFrom = request.getEffectiveFrom() != null ? request.getEffectiveFrom() : Instant.now();
		Instant effectiveTo = request.getEffectiveTo();

		if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "effectiveTo phải sau effectiveFrom");
		}

		validateNoOverlap(user.getId(), effectiveFrom, effectiveTo, null);

		PositionAssignment assignment = PositionAssignment.builder()
						.userLogin(user)
						.orgNode(orgNode)
						.positionCd(request.getPositionCd().toUpperCase())
						.effectiveFrom(effectiveFrom)
						.effectiveTo(effectiveTo)
						.deleteFlg(false)
						.build();

		PositionAssignment saved = positionAssignmentRepository.save(assignment);
		log.info("Đã tạo position_assignment id={} cho user={} org={} position={}",
						saved.getId(), user.getUsername(), orgNode.getNameVn(), saved.getPositionCd());
		return toResponse(saved);
	}

	@Override
	@Transactional
	public PositionAssignmentResponse update(Long id, PositionAssignmentUpdateRequest request) {
		PositionAssignment assignment = findById(id);

		if (request.getEffectiveTo() != null) {
			if (!request.getEffectiveTo().isAfter(assignment.getEffectiveFrom())) {
				throw new BusinessException(ErrorCode.BAD_REQUEST, "effectiveTo phải sau effectiveFrom");
			}
			assignment.setEffectiveTo(request.getEffectiveTo());
		}

		if (request.getActualDismissalDate() != null) {
			if (assignment.getActualDismissalDate() != null) {
				throw new BusinessException(ErrorCode.POSITION_ASSIGNMENT_ALREADY_DISMISSED);
			}
			assignment.setActualDismissalDate(request.getActualDismissalDate());
			assignment.setDismissalReasonCd(request.getDismissalReasonCd());
			assignment.setDismissalNote(request.getDismissalNote());
		}

		PositionAssignment saved = positionAssignmentRepository.save(assignment);
		log.info("Đã cập nhật position_assignment id={}", saved.getId());
		return toResponse(saved);
	}

	@Override
	@Transactional
	public void dismiss(Long id, PositionAssignmentUpdateRequest request) {
		PositionAssignment assignment = findById(id);
		if (assignment.getActualDismissalDate() != null) {
			throw new BusinessException(ErrorCode.POSITION_ASSIGNMENT_ALREADY_DISMISSED);
		}

		Instant dismissalDate = request.getActualDismissalDate() != null
						? request.getActualDismissalDate()
						: Instant.now();

		assignment.setActualDismissalDate(dismissalDate);
		assignment.setDismissalReasonCd(request.getDismissalReasonCd());
		assignment.setDismissalNote(request.getDismissalNote());

		positionAssignmentRepository.save(assignment);
		log.info("Đã miễn nhiệm position_assignment id={} lý do={}", id, request.getDismissalReasonCd());
	}

	@Override
	@Transactional
	public void delete(Long id) {
		PositionAssignment assignment = findById(id);
		assignment.setDeleteFlg(true);
		positionAssignmentRepository.save(assignment);
		log.info("Đã xóa mềm position_assignment id={}", id);
	}

	private PositionAssignment findById(Long id) {
		return positionAssignmentRepository.findById(id)
						.filter(pa -> !pa.isDeleteFlg())
						.orElseThrow(() -> new BusinessException(ErrorCode.POSITION_ASSIGNMENT_NOT_FOUND));
	}

	private void validatePositionCd(String positionCd) {
		if (positionCd == null || !VALID_POSITION_CDS.contains(positionCd.toUpperCase())) {
			throw new BusinessException(ErrorCode.POSITION_CD_INVALID,
							"positionCd phải thuộc " + VALID_POSITION_CDS);
		}
	}

	private void validateNoOverlap(Long userId, Instant from, Instant to, Long excludeId) {
		if (positionAssignmentRepository.existsOverlappingAssignment(userId, from, to, excludeId)) {
			throw new BusinessException(ErrorCode.POSITION_ASSIGNMENT_OVERLAP);
		}
	}

	private PositionAssignmentResponse toResponse(PositionAssignment pa) {
		return PositionAssignmentResponse.builder()
						.id(pa.getId())
						.userLoginId(pa.getUserLogin() != null ? pa.getUserLogin().getId() : null)
						.username(pa.getUserLogin() != null ? pa.getUserLogin().getUsername() : null)
						.orgNodeId(pa.getOrgNode() != null ? pa.getOrgNode().getId() : null)
						.orgNodeNameVn(pa.getOrgNode() != null ? pa.getOrgNode().getNameVn() : null)
						.positionCd(pa.getPositionCd())
						.positionName(resolvePositionName(pa.getPositionCd()))
						.effectiveFrom(pa.getEffectiveFrom())
						.effectiveTo(pa.getEffectiveTo())
						.actualDismissalDate(pa.getActualDismissalDate())
						.dismissalReasonCd(pa.getDismissalReasonCd())
						.dismissalNote(pa.getDismissalNote())
						.deleteFlg(pa.isDeleteFlg())
						.createdDate(pa.getCreatedDate())
						.updatedDate(pa.getUpdatedDate())
						.build();
	}

	private String resolvePositionName(String positionCd) {
		if (positionCd == null) return null;
		return switch (positionCd) {
			case "HT" -> "Hiệu trưởng";
			case "PHT" -> "Phó Hiệu trưởng";
			case "TK" -> "Trưởng khoa";
			case "PK" -> "Phó Trưởng khoa";
			case "TP" -> "Trưởng phòng";
			case "PP" -> "Phó Trưởng phòng";
			case "CV" -> "Chuyên viên";
			default -> positionCd;
		};
	}
}
