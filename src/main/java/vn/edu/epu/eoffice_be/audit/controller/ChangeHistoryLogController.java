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
import vn.edu.epu.eoffice_be.audit.dto.request.ChangeHistoryLogFilterRequest;
import vn.edu.epu.eoffice_be.audit.dto.response.ChangeHistoryLogResponse;
import vn.edu.epu.eoffice_be.audit.service.ChangeHistoryLogQueryService;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;

@RestController
@RequestMapping("/change-history-logs")
@RequiredArgsConstructor
@Auditable
@Tag(name = "Change History Log", description = "Lịch sử thay đổi dữ liệu entity: CREATE/UPDATE/DELETE kèm snapshot trước/sau")
public class ChangeHistoryLogController {

    private final ChangeHistoryLogQueryService changeHistoryLogQueryService;

    @Operation(
            summary = "Tra cứu lịch sử thay đổi dữ liệu",
            description = "Phân trang lịch sử CREATE/UPDATE/DELETE của entity có @TrackChange, gồm beforeData/afterData/changedFields. "
                    + "Filter theo tableName, objectId, changedBy, action, changedFrom/changedTo (ISO 8601). "
                    + "Mặc định sort changedAt giảm dần."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trả về trang kết quả"),
            @ApiResponse(responseCode = "400", description = "Tham số filter/pagination không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực (UNAUTHORIZED)"),
            @ApiResponse(responseCode = "403", description = "Không có quyền xem audit log (FORBIDDEN)")
    })
    @GetMapping
    public BaseResponse<Page<@NonNull ChangeHistoryLogResponse>> filter(
            @Valid @ModelAttribute ChangeHistoryLogFilterRequest request
    ) {
        return BaseResponse.success(changeHistoryLogQueryService.filter(request));
    }
}
