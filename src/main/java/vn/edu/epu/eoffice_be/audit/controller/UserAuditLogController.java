package vn.edu.epu.eoffice_be.audit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.epu.eoffice_be.audit.dto.request.UserAuditLogFilterRequest;
import vn.edu.epu.eoffice_be.audit.dto.response.UserAuditLogResponse;
import vn.edu.epu.eoffice_be.audit.service.UserAuditLogQueryService;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;

@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
@Auditable
@Tag(name = "User Audit Log", description = "Truy vết hoạt động người dùng tại tầng API: ai gọi, endpoint nào, lúc nào")
public class UserAuditLogController {

	private final UserAuditLogQueryService userAuditLogQueryService;

	@Operation(
					summary = "Tra cứu nhật ký hoạt động",
					description = "Phân trang nhật ký ghi tại controller boundary (annotation @Auditable): username, user agent, client IP, "
									+ "request URI, method, thời gian xử lý. Filter theo username (contains), requestUri (contains), "
									+ "createdFrom/createdTo (ISO 8601). Mặc định sort createdDate giảm dần."
	)
	@ApiResponses({
					@ApiResponse(responseCode = "200", description = "Trả về trang kết quả"),
					@ApiResponse(responseCode = "400", description = "Tham số filter/pagination không hợp lệ"),
					@ApiResponse(responseCode = "401", description = "Chưa xác thực (UNAUTHORIZED)"),
					@ApiResponse(responseCode = "403", description = "Không có quyền xem audit log (FORBIDDEN)")
	})
	@GetMapping
	public BaseResponse<Page<@NonNull UserAuditLogResponse>> filter(
					@Valid @ModelAttribute UserAuditLogFilterRequest request
	) {
		return BaseResponse.success(userAuditLogQueryService.filter(request));
	}
}
