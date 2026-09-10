package vn.edu.epu.eoffice_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.dto.request.ChangeRoleRequest;
import vn.edu.epu.eoffice_be.dto.request.CreateUserRequest;
import vn.edu.epu.eoffice_be.dto.request.UpdateUserRequest;
import vn.edu.epu.eoffice_be.dto.request.UserFilterRequest;
import vn.edu.epu.eoffice_be.dto.response.UserDetailResponse;
import vn.edu.epu.eoffice_be.dto.response.UserSummaryResponse;
import vn.edu.epu.eoffice_be.service.UserManagementService;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Auditable
@PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM_ADMIN')")
@Tag(name = "Admin User Management", description = "Quản lý user bởi admin: danh sách, chi tiết, tạo, sửa, "
				+ "xóa mềm/khôi phục, khóa/mở khóa, cấm/kích hoạt, đổi vai trò. Tài khoản SYSTEM_ADMIN chỉ do SYSTEM_ADMIN quản lý.")
public class AdminUserController {

	private final UserManagementService userManagementService;

	@Operation(summary = "Danh sách user", description = "Tra cứu phân trang user với filter (từ khóa, vai trò, trạng thái, đã xóa).")
	@GetMapping
	public BaseResponse<Page<UserSummaryResponse>> list(
					@Valid @ModelAttribute UserFilterRequest filter) {
		return BaseResponse.success(userManagementService.list(filter, filter.pageable()));
	}

	@Operation(summary = "Chi tiết user", description = "Lấy thông tin chi tiết một user theo id.")
	@GetMapping("/{id}")
	public BaseResponse<UserDetailResponse> getDetail(@PathVariable Long id) {
		return BaseResponse.success(userManagementService.getDetail(id));
	}

	@Operation(summary = "Tạo user", description = "Tạo tài khoản mới. Chỉ SYSTEM_ADMIN được tạo tài khoản SYSTEM_ADMIN.")
	@PostMapping
	public BaseResponse<UserDetailResponse> create(@Valid @RequestBody CreateUserRequest request) {
		return BaseResponse.success(userManagementService.create(request));
	}

	@Operation(summary = "Cập nhật user", description = "Cập nhật username/email/role/mustChangePassword. ADMIN không được đổi tài khoản SYSTEM_ADMIN.")
	@PutMapping("/{id}")
	public BaseResponse<UserDetailResponse> update(
					@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
		return BaseResponse.success(userManagementService.update(id, request));
	}

	@Operation(summary = "Xóa mềm user", description = "Đặt delete_flg = true và thu hồi token. Không được xóa SYSTEM_ADMIN cuối cùng.")
	@DeleteMapping("/{id}")
	public BaseResponse<Void> delete(@PathVariable Long id) {
		userManagementService.delete(id);
		return BaseResponse.success(null);
	}

	@Operation(summary = "Khôi phục user", description = "Bỏ xóa mềm (delete_flg = false).")
	@PostMapping("/{id}/restore")
	public BaseResponse<Void> restore(@PathVariable Long id) {
		userManagementService.restore(id);
		return BaseResponse.success(null);
	}

	@Operation(summary = "Khóa user", description = "Chuyển trạng thái sang LOCKED, thu hồi token đăng nhập.")
	@PostMapping("/{id}/lock")
	public BaseResponse<Void> lock(@PathVariable Long id) {
		userManagementService.lock(id);
		return BaseResponse.success(null);
	}

	@Operation(summary = "Mở khóa user", description = "Chuyển trạng thái sang ACTIVE, reset số lần đăng nhập sai.")
	@PostMapping("/{id}/unlock")
	public BaseResponse<Void> unlock(@PathVariable Long id) {
		userManagementService.unlock(id);
		return BaseResponse.success(null);
	}

	@Operation(summary = "Cấm user", description = "Chuyển trạng thái sang DISABLED, thu hồi token. Không được cấm SYSTEM_ADMIN cuối cùng.")
	@PostMapping("/{id}/ban")
	public BaseResponse<Void> ban(@PathVariable Long id) {
		userManagementService.ban(id);
		return BaseResponse.success(null);
	}

	@Operation(summary = "Kích hoạt user", description = "Chuyển trạng thái sang ACTIVE, reset khóa, thu hồi token.")
	@PostMapping("/{id}/activate")
	public BaseResponse<Void> activate(@PathVariable Long id) {
		userManagementService.activate(id);
		return BaseResponse.success(null);
	}

	@Operation(summary = "Đổi vai trò user", description = "Đổi role. Chỉ SYSTEM_ADMIN được đổi sang/đổi từ SYSTEM_ADMIN; không hạ cấp SYSTEM_ADMIN cuối cùng.")
	@PostMapping("/{id}/role")
	public BaseResponse<UserDetailResponse> changeRole(
					@PathVariable Long id, @Valid @RequestBody ChangeRoleRequest request) {
		return BaseResponse.success(userManagementService.changeRole(id, request));
	}
}
