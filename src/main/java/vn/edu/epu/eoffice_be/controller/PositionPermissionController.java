// Cung cấp API gán quyền ký nháy cho chức vụ.
package vn.edu.epu.eoffice_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.dto.request.PositionDraftSignRequest;
import vn.edu.epu.eoffice_be.dto.response.PositionPermissionResponse;
import vn.edu.epu.eoffice_be.service.PositionPermissionService;

// Tiếp nhận yêu cầu quản trị quyền chức vụ và ghi audit.
@RestController
@RequestMapping("/position-permissions")
@RequiredArgsConstructor
@Auditable
@PreAuthorize("isAuthenticated()")
@Tag(name = "Position Permission", description = "Quản lý quyền ký nháy theo chức vụ")
public class PositionPermissionController {
    private final PositionPermissionService service;

    // Gán quyền ký nháy và trả về cấu hình đã lưu.
    @PutMapping("/{positionCd}/draft-sign")
    @Operation(summary = "Gán quyền ký nháy cho chức vụ",
            description = "ADMIN và SYSTEM_ADMIN theo role hiện hành trong database. Quyền áp dụng theo bổ nhiệm đang hiệu lực; người ký vẫn phải được giao xử lý văn bản.")
    public BaseResponse<PositionPermissionResponse> assignDraftSign(
            @PathVariable String positionCd, @Valid @RequestBody PositionDraftSignRequest request) {
        return BaseResponse.success(service.assignDraftSign(positionCd, request));
    }
}
