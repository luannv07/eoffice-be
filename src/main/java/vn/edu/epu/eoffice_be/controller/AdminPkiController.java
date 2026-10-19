package vn.edu.epu.eoffice_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.dto.response.PkiCaStatusResponse;
import vn.edu.epu.eoffice_be.service.PkiCaService;

@RestController
@RequestMapping("/admin/pki/ca")
@RequiredArgsConstructor
@Auditable
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
@Tag(name = "Admin PKI", description = "Kiểm tra và khởi tạo Root CA trên máy chủ")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Thiếu hoặc không hợp lệ JWT"),
        @ApiResponse(responseCode = "403", description = "Yêu cầu vai trò SYSTEM_ADMIN"),
        @ApiResponse(responseCode = "503", description = "PKI_STORAGE_ERROR: không truy cập được nơi lưu PKI")
})
public class AdminPkiController {

    private final PkiCaService pkiCaService;

    @GetMapping("/status")
    @Operation(summary = "Kiểm tra trạng thái Root CA", description = "Chỉ trả về trạng thái và thông tin chứng thư công khai, không trả khóa bí mật hoặc mật khẩu.")
    public BaseResponse<PkiCaStatusResponse> status() {
        return BaseResponse.success(pkiCaService.status());
    }

    @PostMapping("/initialize")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Khởi tạo Root CA", description = "Dùng cấu hình máy chủ; không nhận đường dẫn hay mật khẩu từ client. Từ chối nếu CA hoặc dữ liệu PKI trước đó đã tồn tại.")
    @ApiResponse(responseCode = "409", description = "PKI_CA_ALREADY_EXISTS hoặc PKI_CA_RECOVERY_REQUIRED: giữ nguyên bộ file, không tạo đè")
    public BaseResponse<PkiCaStatusResponse> initialize() {
        return BaseResponse.success(pkiCaService.initialize());
    }
}
