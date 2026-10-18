package vn.edu.epu.eoffice_be.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.edu.epu.eoffice_be.dto.request.PositionAssignmentCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.PositionAssignmentUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.PositionAssignmentResponse;

import java.time.Instant;

public interface PositionAssignmentService {

	Page<PositionAssignmentResponse> listByUser(Long userLoginId, Pageable pageable);

	Page<PositionAssignmentResponse> listByOrgNode(Long orgNodeId, Pageable pageable);

	Page<PositionAssignmentResponse> listActive(Long userId, Instant atTime, Pageable pageable);

	PositionAssignmentResponse getDetail(Long id);

	PositionAssignmentResponse create(PositionAssignmentCreateRequest request);

	PositionAssignmentResponse update(Long id, PositionAssignmentUpdateRequest request);

	void dismiss(Long id, PositionAssignmentUpdateRequest request);

	void delete(Long id);
}
