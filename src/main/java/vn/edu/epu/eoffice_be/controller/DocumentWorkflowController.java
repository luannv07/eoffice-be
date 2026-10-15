package vn.edu.epu.eoffice_be.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.dto.PendingDocumentResponse;
import vn.edu.epu.eoffice_be.dto.WorkflowTimelineResponse;
import vn.edu.epu.eoffice_be.dto.request.DocumentApproveRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentInitialSignRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentPublishRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRejectRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRevisionRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRevokeRequest;
import vn.edu.epu.eoffice_be.dto.response.DocumentDetailResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentSignatureVerifyResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentWorkflowLogResponse;
import vn.edu.epu.eoffice_be.service.DocumentWorkflowService;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@Auditable
@PreAuthorize("isAuthenticated()")
@Tag(name = "Document Workflow", description = "Quản lý luồng phê duyệt văn bản, bút phê lãnh đạo (UC12), ký nháy PDF (UC13) và ký số phát hành PKI (UC14)")
public class DocumentWorkflowController {

    private final DocumentWorkflowService documentWorkflowService;

    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem chi tiết văn bản")
    @GetMapping("/{id}")
    public BaseResponse<DocumentDetailResponse> getDetail(@PathVariable Long id) {
        return BaseResponse.success(documentWorkflowService.getDetail(id));
    }

    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lịch sử luồng duyệt (Timeline)")
    @GetMapping("/{id}/workflow-logs")
    public BaseResponse<Page<DocumentWorkflowLogResponse>> getWorkflowLogs(
            @PathVariable Long id,
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return BaseResponse.success(documentWorkflowService.getWorkflowHistory(id, pageable));
    }

    @PreAuthorize("hasAnyRole('LEADER', 'REVIEWER', 'ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Duyệt văn bản (UC12)")
    @PostMapping("/{id}/workflow/approve")
    public BaseResponse<DocumentDetailResponse> approve(
            @PathVariable Long id,
            @Valid @RequestBody DocumentApproveRequest request
    ) {
        return BaseResponse.success(documentWorkflowService.approve(id, request));
    }

    @PreAuthorize("hasAnyRole('LEADER', 'REVIEWER', 'ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Yêu cầu chỉnh sửa / Trả về (UC12)")
    @PostMapping("/{id}/workflow/request-revision")
    public BaseResponse<DocumentDetailResponse> requestRevision(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRevisionRequest request
    ) {
        return BaseResponse.success(documentWorkflowService.requestRevision(id, request));
    }

    @PreAuthorize("hasAnyRole('REVIEWER', 'LEADER', 'ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Ký nháy văn bản (UC13)")
    @PostMapping("/{id}/workflow/initial-sign")
    public BaseResponse<DocumentDetailResponse> initialSign(
            @PathVariable Long id,
            @Valid @RequestBody DocumentInitialSignRequest request
    ) {
        return BaseResponse.success(documentWorkflowService.initialSign(id, request));
    }

    @PreAuthorize("hasAnyRole('LEADER', 'ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Phê duyệt và Ký phát hành chính thức (UC14)")
    @PostMapping("/{id}/workflow/sign-and-publish")
    public BaseResponse<DocumentDetailResponse> signAndPublish(
            @PathVariable Long id,
            @Valid @RequestBody DocumentPublishRequest request
    ) {
        return BaseResponse.success(documentWorkflowService.signAndPublish(id, request));
    }

    @PreAuthorize("hasAnyRole('LEADER', 'REVIEWER', 'ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Từ chối phê duyệt (UC14)")
    @PostMapping("/{id}/workflow/reject")
    public BaseResponse<DocumentDetailResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRejectRequest request
    ) {
        return BaseResponse.success(documentWorkflowService.reject(id, request));
    }

    @PreAuthorize("hasAnyRole('LEADER', 'ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Thu hồi văn bản đã ban hành chính thức")
    @PostMapping("/{id}/workflow/revoke")
    public BaseResponse<DocumentDetailResponse> revokeDocument(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRevokeRequest request
    ) {
        return BaseResponse.success(documentWorkflowService.revokeDocument(id, request));
    }

    // UC17.1: Xem timeline luồng điều hành & bút phê lãnh đạo
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem timeline luồng điều hành & bút phê lãnh đạo")
    @GetMapping("/{id}/timeline")
    public BaseResponse<List<WorkflowTimelineResponse>> getDocumentTimeline(
            @PathVariable("id") Long id,
            org.springframework.security.core.Authentication authentication
    ) {
        List<WorkflowTimelineResponse> timeline = documentWorkflowService.getDocumentTimeline(id, authentication);
        return BaseResponse.success(timeline);
    }

    @PreAuthorize("isAuthenticated()")
    public BaseResponse<List<WorkflowTimelineResponse>> getDocumentTimeline(Long id) {
        return getDocumentTimeline(id, org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
    }

    // UC17.2: Danh sách văn bản tồn đọng / chờ xử lý kèm số ngày chờ SLA
    @PreAuthorize("hasAnyRole('LEADER', 'REVIEWER', 'ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Danh sách văn bản tồn đọng / chờ xử lý kèm số ngày chờ SLA")
    @GetMapping("/pending-review")
    public BaseResponse<Page<PendingDocumentResponse>> getPendingReviewDocuments(
            @PageableDefault(size = 10) Pageable pageable) {
        Page<PendingDocumentResponse> page = documentWorkflowService.getPendingDocuments(pageable);
        return BaseResponse.success(page);
    }

    // UC-NEW-1: Danh sách văn bản chờ ký số
    @PreAuthorize("hasAnyRole('LEADER', 'ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Danh sách văn bản chờ ký số (PENDING_SIGN)")
    @GetMapping("/pending-sign")
    public BaseResponse<Page<PendingDocumentResponse>> getPendingSignDocuments(
            @PageableDefault(size = 10) Pageable pageable) {
        return BaseResponse.success(documentWorkflowService.getPendingSignDocuments(pageable));
    }

    // UC-NEW-2: Danh sách văn bản chờ ban hành
    @PreAuthorize("hasAnyRole('CLERK', 'LEADER', 'REVIEWER', 'ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Danh sách văn bản chờ ban hành    (PENDING_ISSUANCE)")
    @GetMapping("/pending-publish")
    public BaseResponse<Page<PendingDocumentResponse>> getPendingPublishDocuments(
            @PageableDefault(size = 10) Pageable pageable) {
        return BaseResponse.success(documentWorkflowService.getPendingPublishDocuments(pageable));
    }

    // UC-SIG-VERIFY: Kiểm định 7 tiêu chí bảo mật chữ ký số PDF (yêu cầu quyền truy cập văn bản)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Kiểm định 7 tiêu chí bảo mật toàn bộ chữ ký số trên tài liệu PDF (yêu cầu quyền xem văn bản)")
    @GetMapping("/{id}/signatures/verify")
    public BaseResponse<DocumentSignatureVerifyResponse> verifySignatures(@PathVariable Long id) {
        return BaseResponse.success(documentWorkflowService.verifyDocumentSignatures(id));
    }
}
