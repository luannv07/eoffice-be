package vn.edu.epu.eoffice_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.dto.request.PositionAssignmentCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.PositionAssignmentUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.PositionAssignmentResponse;
import vn.edu.epu.eoffice_be.service.PositionAssignmentService;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/position-assignments")
@RequiredArgsConstructor
@Auditable
@PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM_ADMIN')")
@Tag(name = "Position Assignment", description = "Quản lý quyết định bổ nhiệm chức vụ cho tài khoản")
public class PositionAssignmentController {

	private final PositionAssignmentService positionAssignmentService;

	@Operation(summary = "Danh sách bổ nhiệm theo user", description = "Trả về tất cả quyết định bổ nhiệm (kể cả đã hết hiệu lực) của một tài khoản.")
	@ApiResponses({
					@ApiResponse(responseCode = "200", description = "Danh sách bổ nhiệm của user")
	})
	@GetMapping("/user/{userLoginId}")
	public BaseResponse<List<PositionAssignmentResponse>> listByUser(@PathVariable Long userLoginId) {
		return BaseResponse.success(positionAssignmentService.listByUser(userLoginId));
	}

	@Operation(summary = "Danh sách bổ nhiệm theo đơn vị", description = "Trả về tất cả quyết định bổ nhiệm trong một đơn vị tổ chức.")
	@ApiResponses({
					@ApiResponse(responseCode = "200", description = "Danh sách bổ nhiệm của đơn vị")
	})
	@GetMapping("/org-node/{orgNodeId}")
	public BaseResponse<List<PositionAssignmentResponse>> listByOrgNode(@PathVariable Long orgNodeId) {
		return BaseResponse.success(positionAssignmentService.listByOrgNode(orgNodeId));
	}

	@Operation(summary = "Danh sách bổ nhiệm đang hiệu lực", description = "Trả về các quyết định đang hiệu lực tại thời điểm cho trước (mặc định: hiện tại).")
	@ApiResponses({
					@ApiResponse(responseCode = "200", description = "Danh sách bổ nhiệm đang hiệu lực")
	})
	@GetMapping("/user/{userLoginId}/active")
	public BaseResponse<List<PositionAssignmentResponse>> listActive(
					@PathVariable Long userLoginId,
					@RequestParam(required = false) Instant atTime) {
		return BaseResponse.success(positionAssignmentService.listActive(userLoginId, atTime));
	}

	@Operation(summary = "Chi tiết bổ nhiệm", description = "Lấy thông tin chi tiết một quyết định bổ nhiệm theo id.")
	@ApiResponses({
					@ApiResponse(responseCode = "200", description = "Tìm thấy bổ nhiệm"),
					@ApiResponse(responseCode = "404", description = "Không tìm thấy (POSITION_ASSIGNMENT_NOT_FOUND)")
	})
	@GetMapping("/{id}")
	public BaseResponse<PositionAssignmentResponse> getDetail(@PathVariable Long id) {
		return BaseResponse.success(positionAssignmentService.getDetail(id));
	}

	@Operation(
					summary = "Tạo bổ nhiệm mới",
					description = "Tạo quyết định bổ nhiệm chức vụ cho tài tại đơn vị. "
									+ "positionCd phải thuộc {HT, PHT, TK, PK, TP, PP, CV}. "
									+ "Hệ thống chặn trùng lắp khoảng thời gian hiệu lực cho cùng một tài khoản."
	)
	@ApiResponses({
					@ApiResponse(responseCode = "200", description = "Tạo thành công"),
					@ApiResponse(responseCode = "400", description = "positionCd không hợp lệ hoặc effectiveTo <= effectiveFrom (POSITION_CD_INVALID / BAD_REQUEST)"),
					@ApiResponse(responseCode = "404", description = "Không tìm thấy tài khoản hoặc đơn vị (NOT_FOUND / ORG_NODE_NOT_FOUND)"),
					@ApiResponse(responseCode = "409", description = "Trùng lắp thời gian hiệu lực (POSITION_ASSIGNMENT_OVERLAP)")
	})
	@PostMapping
	public BaseResponse<PositionAssignmentResponse> create(
					@Valid @RequestBody PositionAssignmentCreateRequest request) {
		return BaseResponse.success(positionAssignmentService.create(request));
	}

	@Operation(
					summary = "Cập nhật bổ nhiệm",
					description = "Kéo dài effectiveTo hoặc ghi nhận miễn nhiệm (actualDismissalDate + lý do)."
	)
	@ApiResponses({
					@ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
					@ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ hoặc đã miễn nhiệm trước đó (POSITION_ASSIGNMENT_ALREADY_DISMISSED)"),
					@ApiResponse(responseCode = "404", description = "Không tìm thấy (POSITION_ASSIGNMENT_NOT_FOUND)")
	})
	@PutMapping("/{id}")
	public BaseResponse<PositionAssignmentResponse> update(
					@PathVariable Long id,
					@Valid @RequestBody PositionAssignmentUpdateRequest request) {
		return BaseResponse.success(positionAssignmentService.update(id, request));
	}

	@Operation(summary = "Miễn nhiệm sớm", description = "Kết thúc bổ nhiệm trước ngày hết hiệu lực, kèm lý do miễn nhiệm.")
	@ApiResponses({
					@ApiResponse(responseCode = "200", description = "Miễn nhiệm thành công"),
					@ApiResponse(responseCode = "400", description = "Đã miễn nhiệm trước đó (POSITION_ASSIGNMENT_ALREADY_DISMISSED)"),
					@ApiResponse(responseCode = "404", description = "Không tìm thấy (POSITION_ASSIGNMENT_NOT_FOUND)")
	})
	@PostMapping("/{id}/dismiss")
	public BaseResponse<Void> dismiss(
					@PathVariable Long id,
					@Valid @RequestBody PositionAssignmentUpdateRequest request) {
		positionAssignmentService.dismiss(id, request);
		return BaseResponse.success(null);
	}

	@Operation(summary = "Xóa mềm bổ nhiệm", description = "Đặt delete_flg = true. Không xóa vật lý để giữ audit trace.")
	@ApiResponses({
					@ApiResponse(responseCode = "200", description = "Xóa thành công"),
					@ApiResponse(responseCode = "404", description = "Không tìm thấy (POSITION_ASSIGNMENT_NOT_FOUND)")
	})
	@DeleteMapping("/{id}")
	public BaseResponse<Void> delete(@PathVariable Long id) {
		positionAssignmentService.delete(id);
		return BaseResponse.success(null);
	}
}
