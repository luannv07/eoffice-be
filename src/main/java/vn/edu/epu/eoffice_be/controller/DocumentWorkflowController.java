package vn.edu.epu.eoffice_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.dto.request.DocumentApproveRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentInitialSignRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentPublishRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRejectRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRevisionRequest;
import vn.edu.epu.eoffice_be.dto.response.DocumentDetailResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentWorkflowLogResponse;
import vn.edu.epu.eoffice_be.service.DocumentWorkflowService;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@Auditable
@Tag(name = "Document Workflow", description = "Quản lý luồng phê duyệt văn bản, bút phê lãnh đạo (UC12), ký nháy PDF (UC13) và ký số phát hành PKI (UC14)")
public class DocumentWorkflowController {

    private final DocumentWorkflowService documentWorkflowService;

    @Operation(
            summary = "Xem chi tiết văn bản",
            description = "Lấy toàn bộ thông tin chi tiết của văn bản theo ID, bao gồm trạng thái duyệt, số hiệu, file nháp và file chính thức."
    )
    @ApiResponse(responseCode = "200", description = "Lấy thông tin thành công")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy văn bản (DOCUMENT_NOT_FOUND)")
    @GetMapping("/{id}")
    public BaseResponse<DocumentDetailResponse> getDetail(@PathVariable Long id) {
        return BaseResponse.success(documentWorkflowService.getDetail(id));
    }

    @Operation(
            summary = "Lịch sử luồng duyệt (Timeline)",
            description = "Xem toàn bộ quá trình luân chuyển, phê duyệt, ký nháy, nhận xét và bút phê lãnh đạo của văn bản theo thứ tự thời gian có hỗ trợ phân trang."
    )
    @ApiResponse(responseCode = "200", description = "Lấy lịch sử thành công")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy văn bản (DOCUMENT_NOT_FOUND)")
    @GetMapping("/{id}/workflow-logs")
    public BaseResponse<Page<DocumentWorkflowLogResponse>> getWorkflowLogs(
            @PathVariable Long id,
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return BaseResponse.success(documentWorkflowService.getWorkflowHistory(id, pageable));
    }

    @Operation(
            summary = "Duyệt văn bản (UC12)",
            description = "Người duyệt chấp thuận văn bản, chuyển bước tiếp theo trong luồng. Hỗ trợ ghi nhận Bút phê lãnh đạo và phân công đơn vị chủ trì."
    )
    @ApiResponse(responseCode = "200", description = "Duyệt văn bản thành công")
    @ApiResponse(responseCode = "400", description = "Trạng thái không hợp lệ để duyệt")
    @ApiResponse(responseCode = "403", description = "Không có quyền thực hiện hoặc chưa xác thực")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy văn bản (DOCUMENT_NOT_FOUND)")
    @PostMapping("/{id}/workflow/approve")
    public BaseResponse<DocumentDetailResponse> approve(
            @PathVariable Long id,
            @Valid @RequestBody DocumentApproveRequest request
    ) {
        return BaseResponse.success(documentWorkflowService.approve(id, request));
    }

    @Operation(
            summary = "Yêu cầu chỉnh sửa / Trả về (UC12)",
            description = "Người duyệt từ chối tiếp nhận và trả lại cho người soạn thảo chỉnh sửa (chuyển trạng thái sang NEEDS_REVISION). Bắt buộc nhập lý do."
    )
    @ApiResponse(responseCode = "200", description = "Trả về văn bản thành công")
    @ApiResponse(responseCode = "400", description = "Thiếu lý do hoặc trạng thái không hợp lệ")
    @ApiResponse(responseCode = "403", description = "Không có quyền thực hiện hoặc chưa xác thực")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy văn bản (DOCUMENT_NOT_FOUND)")
    @PostMapping("/{id}/workflow/request-revision")
    public BaseResponse<DocumentDetailResponse> requestRevision(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRevisionRequest request
    ) {
        return BaseResponse.success(documentWorkflowService.requestRevision(id, request));
    }

    @Operation(
            summary = "Ký nháy văn bản (UC13)",
            description = "Cán bộ thẩm định thực hiện ký nháy. Hệ thống sử dụng Apache PDFBox để nhúng Digital Stamp vào trang cuối PDF và tính mã băm SHA-256."
    )
    @ApiResponse(responseCode = "200", description = "Ký nháy thành công, đã cập nhật file PDF có con dấu")
    @ApiResponse(responseCode = "400", description = "Trạng thái văn bản không hợp lệ để ký nháy")
    @ApiResponse(responseCode = "403", description = "Không có quyền thực hiện hoặc chưa xác thực")
    @ApiResponse(responseCode = "500", description = "Lỗi khi xử lý file PDF hoặc chữ ký")
    @PostMapping("/{id}/workflow/initial-sign")
    public BaseResponse<DocumentDetailResponse> initialSign(
            @PathVariable Long id,
            @Valid @RequestBody DocumentInitialSignRequest request
    ) {
        return BaseResponse.success(documentWorkflowService.initialSign(id, request));
    }

    @Operation(
            summary = "Phê duyệt và Ký phát hành chính thức (UC14)",
            description = "Lãnh đạo ký phát hành: Ký số PKI X.509 PAdES, đặt cờ CERTIFIED_NO_CHANGES_ALLOWED, cấp số hiệu công văn và chuyển trạng thái sang PUBLISHED."
    )
    @ApiResponse(responseCode = "200", description = "Ký phát hành thành công, văn bản chính thức đã được niêm phong")
    @ApiResponse(responseCode = "400", description = "Thiếu số hiệu văn bản hoặc trạng thái không hợp lệ")
    @ApiResponse(responseCode = "403", description = "Không có quyền ký phát hành")
    @ApiResponse(responseCode = "500", description = "Lỗi khi ký số điện tử")
    @PostMapping("/{id}/workflow/sign-and-publish")
    public BaseResponse<DocumentDetailResponse> signAndPublish(
            @PathVariable Long id,
            @Valid @RequestBody DocumentPublishRequest request
    ) {
        return BaseResponse.success(documentWorkflowService.signAndPublish(id, request));
    }

    @Operation(
            summary = "Từ chối phê duyệt (UC14)",
            description = "Lãnh đạo từ chối phê duyệt văn bản (chuyển sang REJECTED). Bắt buộc nhập lý do từ chối."
    )
    @ApiResponse(responseCode = "200", description = "Từ chối phê duyệt thành công")
    @ApiResponse(responseCode = "400", description = "Thiếu lý do từ chối")
    @ApiResponse(responseCode = "403", description = "Không có quyền từ chối hoặc chưa xác thực")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy văn bản")
    @PostMapping("/{id}/workflow/reject")
    public BaseResponse<DocumentDetailResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRejectRequest request
    ) {
        return BaseResponse.success(documentWorkflowService.reject(id, request));
    }
}
