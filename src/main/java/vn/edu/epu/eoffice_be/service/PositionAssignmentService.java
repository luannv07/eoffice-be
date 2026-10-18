package vn.edu.epu.eoffice_be.service;

import vn.edu.epu.eoffice_be.dto.request.PositionAssignmentCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.PositionAssignmentUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.PositionAssignmentResponse;

import java.time.Instant;
import java.util.List;

public interface PositionAssignmentService {

	List<PositionAssignmentResponse> listByUser(Long userLoginId);

	List<PositionAssignmentResponse> listByOrgNode(Long orgNodeId);

	List<PositionAssignmentResponse> listActive(Long userId, Instant atTime);

	PositionAssignmentResponse getDetail(Long id);

	PositionAssignmentResponse create(PositionAssignmentCreateRequest request);

	PositionAssignmentResponse update(Long id, PositionAssignmentUpdateRequest request);

	void dismiss(Long id, PositionAssignmentUpdateRequest request);

	void delete(Long id);
}
