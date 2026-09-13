package vn.edu.epu.eoffice_be.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @Operation(summary = "Xem chi tiết văn bản")
    @GetMapping("/{id}")
    public BaseResponse<DocumentDetailResponse> getDetail(@PathVariable Long id) {
        return BaseResponse.success(documentWorkflowService.getDetail(id));
    }

    @Operation(summary = "Lịch sử luồng duyệt (Timeline)")
    @GetMapping("/{id}/workflow-logs")
    public BaseResponse<Page<DocumentWorkflowLogResponse>> getWorkflowLogs(
            @PathVariable Long id,
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return BaseResponse.success(documentWorkflowService.getWorkflowHistory(id, pageable));
    }

    @Operation(summary = "Duyệt văn bản (UC12)")
    @PostMapping("/{id}/workflow/approve")
    public BaseResponse<DocumentDetailResponse> approve(
            @PathVariable Long id,
            @Valid @RequestBody DocumentApproveRequest request
    ) {
        return BaseResponse.success(documentWorkflowService.approve(id, request));
    }

    @Operation(summary = "Yêu cầu chỉnh sửa / Trả về (UC12)")
    @PostMapping("/{id}/workflow/request-revision")
    public BaseResponse<DocumentDetailResponse> requestRevision(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRevisionRequest request
    ) {
        return BaseResponse.success(documentWorkflowService.requestRevision(id, request));
    }

    @Operation(summary = "Ký nháy văn bản (UC13)")
    @PostMapping("/{id}/workflow/initial-sign")
    public BaseResponse<DocumentDetailResponse> initialSign(
            @PathVariable Long id,
            @Valid @RequestBody DocumentInitialSignRequest request
    ) {
        return BaseResponse.success(documentWorkflowService.initialSign(id, request));
    }

    @Operation(summary = "Phê duyệt và Ký phát hành chính thức (UC14)")
    @PostMapping("/{id}/workflow/sign-and-publish")
    public BaseResponse<DocumentDetailResponse> signAndPublish(
            @PathVariable Long id,
            @Valid @RequestBody DocumentPublishRequest request
    ) {
        return BaseResponse.success(documentWorkflowService.signAndPublish(id, request));
    }

    @Operation(summary = "Từ chối phê duyệt (UC14)")
    @PostMapping("/{id}/workflow/reject")
    public BaseResponse<DocumentDetailResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRejectRequest request
    ) {
        return BaseResponse.success(documentWorkflowService.reject(id, request));
    }

    // UC17.1: Xem timeline luồng điều hành & bút phê lãnh đạo
    @GetMapping("/{id}/timeline")
    public ResponseEntity<List<WorkflowTimelineResponse>> getDocumentTimeline(@PathVariable("id") Long id) {
        List<WorkflowTimelineResponse> timeline = documentWorkflowService.getDocumentTimeline(id);
        return ResponseEntity.status(HttpStatus.OK).body(timeline);
    }

    // UC17.2: Danh sách văn bản tồn đọng / chờ xử lý kèm số ngày chờ SLA
    @GetMapping("/pending-review")
    public ResponseEntity<Page<PendingDocumentResponse>> getPendingReviewDocuments(
            @PageableDefault(size = 10) Pageable pageable) {
        var pendingList = documentWorkflowService.getPendingDocuments();
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), pendingList.size());
        var subList = (start > pendingList.size()) ? List.<PendingDocumentResponse>of() : pendingList.subList(start, end);
        return ResponseEntity.ok(new PageImpl<>(subList, pageable, pendingList.size()));
    }
}