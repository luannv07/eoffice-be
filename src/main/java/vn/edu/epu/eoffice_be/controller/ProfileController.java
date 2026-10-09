package vn.edu.epu.eoffice_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.dto.request.CompleteProfileRequest;
import vn.edu.epu.eoffice_be.dto.response.CompleteProfileResponse;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.service.ProfileService;

@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
@Auditable
@Tag(name = "Profile", description = "Hồ sơ cá nhân: user tự hoàn thiện hồ sơ (upsert user_infos) và nhận accessToken mới. Đã exempt khỏi ProfileCompletedFilter nên user mới (profileCompleted=false) vẫn gọi được.")
public class ProfileController {

	private final ProfileService profileService;

	@Operation(
					summary = "Hoàn thiện hồ sơ của chính mình",
					description = "Upsert bản ghi user_infos của user đang xác thực (lần đầu tạo mới, các lần sau cập nhật). "
									+ "Trả về hồ sơ mới kèm accessToken mới có claim profileCompleted=true — FE thay token ngay, không cần đăng nhập lại."
	)
	@ApiResponses({
					@ApiResponse(responseCode = "200", description = "Hoàn thiện hồ sơ thành công"),
					@ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ (VALIDATION_ERROR / BUSINESS_ERROR)"),
					@ApiResponse(responseCode = "401", description = "Chưa xác thực (UNAUTHORIZED)"),
					@ApiResponse(responseCode = "409", description = "Email liên hệ đã được sử dụng (EMAIL_ALREADY_EXISTS)")
	})
	@PutMapping("/me")
	public BaseResponse<CompleteProfileResponse> completeMyProfile(
					Authentication authentication,
					@Valid @RequestBody CompleteProfileRequest request) {
		JwtUserPrincipal principal = (JwtUserPrincipal) authentication.getPrincipal();
		return BaseResponse.success(profileService.completeMyProfile(principal.userId(), request));
	}
}
