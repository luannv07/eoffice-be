package vn.edu.epu.eoffice_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.DocumentCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentFilterRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentUpdateRequest;
import vn.edu.epu.eoffice_be.dto.request.RecallApprovalRequest;
import vn.edu.epu.eoffice_be.dto.request.SubmitApprovalRequest;
import vn.edu.epu.eoffice_be.dto.response.DocumentAttachmentResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentDetailResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentVersionResponse;
import vn.edu.epu.eoffice_be.dto.response.EligibleApproverResponse;
import vn.edu.epu.eoffice_be.dto.response.VersionCompareResponse;
import vn.edu.epu.eoffice_be.entity.DocumentAttachment;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.service.DocumentApprovalService;
import vn.edu.epu.eoffice_be.service.DocumentAttachmentService;
import vn.edu.epu.eoffice_be.service.DocumentDraftService;
import vn.edu.epu.eoffice_be.service.DocumentVersionService;

import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@Auditable
@Tag(name = "Document Management", description = "Quản lý văn bản và dự thảo (UC08, UC09, UC10, UC11)")
public class DocumentController {

    private final DocumentDraftService documentDraftService;
    private final DocumentAttachmentService documentAttachmentService;
    private final DocumentVersionService documentVersionService;
    private final DocumentApprovalService documentApprovalService;

    // =========================================================================
    // UC08: TẠO VÀ QUẢN LÝ DỰ THẢO VĂN BẢN
    // =========================================================================

    @Operation(summary = "Tạo dự thảo văn bản (UC08)",
            description = "Tạo mới một dự thảo văn bản. Hệ thống tự động sinh mã dự thảo, gán trạng thái DRAFT và tạo phiên bản 1.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tạo dự thảo thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào không hợp lệ (VALIDATION_ERROR)"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực (UNAUTHORIZED)")
    })
    @PostMapping("/drafts")
    public BaseResponse<DocumentResponse> createDraft(
            @Valid @RequestBody DocumentCreateRequest request,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        return BaseResponse.success(documentDraftService.createDraft(request, userId));
    }

    @Operation(summary = "Xem chi tiết dự thảo văn bản",
            description = "Lấy thông tin chi tiết dự thảo, kèm danh sách các tệp đính kèm và lịch sử các phiên bản.")
    @GetMapping("/{id}")
    public BaseResponse<DocumentDetailResponse> getDraftDetail(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        return BaseResponse.success(documentDraftService.getDraftDetail(id, userId));
    }

    @Operation(summary = "Danh sách dự thảo của người dùng",
            description = "Tìm kiếm và phân trang danh sách dự thảo văn bản do người dùng hiện tại tạo hoặc quản lý.")
    @GetMapping("/drafts")
    public BaseResponse<Page<DocumentResponse>> searchDrafts(
            @Valid @ModelAttribute DocumentFilterRequest filter,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        return BaseResponse.success(documentDraftService.searchDrafts(filter, userId));
    }

    // =========================================================================
    // UC10: CHỈNH SỬA VÀ LƯU PHIÊN BẢN DỰ THẢO
    // =========================================================================

    @Operation(summary = "Chỉnh sửa dự thảo và lưu phiên bản (UC10)",
            description = "Cập nhật tiêu đề, trích yếu, nội dung hoặc đơn vị. Nếu saveNewVersion=true sẽ tự sinh phiên bản snapshot mới.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
            @ApiResponse(responseCode = "400", description = "Trạng thái văn bản không hợp lệ để chỉnh sửa (DOCUMENT_INVALID_STATUS)"),
            @ApiResponse(responseCode = "403", description = "Không có quyền chỉnh sửa (FORBIDDEN)"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy văn bản (DOCUMENT_NOT_FOUND)")
    })
    @PutMapping("/{id}")
    public BaseResponse<DocumentResponse> updateDraft(
            @PathVariable Long id,
            @Valid @RequestBody DocumentUpdateRequest request,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        return BaseResponse.success(documentDraftService.updateDraft(id, request, userId));
    }

    @Operation(summary = "Lịch sử các phiên bản (UC10)",
            description = "Lấy danh sách tất cả các phiên bản đã lưu của dự thảo văn bản.")
    @GetMapping("/{id}/versions")
    public BaseResponse<List<DocumentVersionResponse>> getVersions(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        return BaseResponse.success(documentVersionService.getVersions(id, userId));
    }

    @Operation(summary = "So sánh 2 phiên bản (UC10)",
            description = "Đối chiếu khác biệt về tiêu đề, trích yếu và nội dung giữa 2 phiên bản bất kỳ.")
    @GetMapping("/{id}/versions/compare")
    public BaseResponse<VersionCompareResponse> compareVersions(
            @PathVariable Long id,
            @RequestParam int fromVersion,
            @RequestParam int toVersion,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        return BaseResponse.success(documentVersionService.compareVersions(id, fromVersion, toVersion, userId));
    }

    @Operation(summary = "Khôi phục phiên bản cũ (UC10)",
            description = "Khôi phục nội dung văn bản về một phiên bản quá khứ được chỉ định và tạo bản snapshot mới.")
    @PostMapping("/{id}/versions/{versionNumber}/restore")
    public BaseResponse<DocumentResponse> restoreVersion(
            @PathVariable Long id,
            @PathVariable int versionNumber,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        return BaseResponse.success(documentVersionService.restoreVersion(id, versionNumber, userId));
    }

    // =========================================================================
    // UC09: TẢI FILE DOCX/PDF
    // =========================================================================

    @Operation(summary = "Tải tệp DOCX/PDF đính kèm (UC09)",
            description = "Tải tệp tài liệu đính kèm lên cho dự thảo. Kiểm tra định dạng (chỉ DOCX/PDF), quét an toàn mã độc và dung lượng tối đa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tải tệp lên thành công"),
            @ApiResponse(responseCode = "400", description = "Định dạng không hợp lệ hoặc tệp không an toàn (FILE_INVALID_FORMAT / FILE_UNSAFE)"),
            @ApiResponse(responseCode = "413", description = "Dung lượng vượt quá giới hạn (FILE_SIZE_EXCEEDED)")
    })
    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<DocumentAttachmentResponse> uploadAttachment(
            @PathVariable Long id,
            @Parameter(description = "Tệp đính kèm (chỉ nhận DOCX hoặc PDF, tối đa 25MB)")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Đánh dấu là tệp văn bản chính hay tệp phụ lục")
            @RequestParam(value = "isMain", defaultValue = "false") boolean isMain,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        return BaseResponse.success(documentAttachmentService.uploadAttachment(id, file, isMain, userId));
    }

    @Operation(summary = "Tải về tệp đính kèm (UC09)",
            description = "Tải về hoặc xem trực tuyến tệp DOCX/PDF đính kèm.")
    @GetMapping("/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long id,
            @PathVariable Long attachmentId,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        Resource resource = documentAttachmentService.downloadAttachment(id, attachmentId, userId);
        DocumentAttachment metadata = documentAttachmentService.getAttachmentMetadata(id, attachmentId, userId);

        String rawFileName = metadata.getOriginalFileName();
        String safeFileName = (rawFileName != null && !rawFileName.isBlank()) ? rawFileName : "file";
        String encodedFileName = URLEncoder.encode(safeFileName, StandardCharsets.UTF_8)
                .replace("+", "%20");

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        String mimeTypeStr = metadata.getMimeType();
        if (mimeTypeStr != null && !mimeTypeStr.isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(mimeTypeStr);
            } catch (IllegalArgumentException e) {
                log.warn("MIME type không hợp lệ '{}' cho tệp đính kèm id={}, fallback về APPLICATION_OCTET_STREAM",
                        mimeTypeStr, attachmentId);
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .body(resource);
    }

    @Operation(summary = "Xóa tệp đính kèm (UC09)",
            description = "Xóa tệp đính kèm khỏi dự thảo văn bản khi chưa gửi duyệt.")
    @DeleteMapping("/{id}/attachments/{attachmentId}")
    public BaseResponse<Void> deleteAttachment(
            @PathVariable Long id,
            @PathVariable Long attachmentId,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        documentAttachmentService.deleteAttachment(id, attachmentId, userId);
        return BaseResponse.success(null);
    }

    // =========================================================================
    // UC11: GỬI VĂN BẢN ĐỂ DUYỆT & THU HỒI YÊU CẦU DUYỆT
    // =========================================================================

    @Operation(summary = "Danh sách cán bộ có thể duyệt (UC11)",
            description = "Lấy danh sách các cán bộ có thẩm quyền duyệt văn bản phù hợp với đơn vị ban hành.")
    @GetMapping("/{id}/eligible-approvers")
    public BaseResponse<List<EligibleApproverResponse>> getEligibleApprovers(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        return BaseResponse.success(documentApprovalService.getEligibleApprovers(id, userId));
    }

    @Operation(summary = "Gửi văn bản để duyệt (UC11)",
            description = "Gửi hồ sơ văn bản tới cán bộ duyệt đã chọn. Kiểm tra tính đầy đủ hồ sơ (bắt buộc có tệp đính kèm chính hoặc nội dung).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Gửi duyệt thành công"),
            @ApiResponse(responseCode = "400", description = "Hồ sơ chưa đầy đủ hoặc trạng thái không hợp lệ (DOCUMENT_INCOMPLETE_DOSSIER / DOCUMENT_INVALID_STATUS)")
    })
    @PostMapping("/{id}/submit-approval")
    public BaseResponse<DocumentResponse> submitForApproval(
            @PathVariable Long id,
            @Valid @RequestBody SubmitApprovalRequest request,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        return BaseResponse.success(documentApprovalService.submitForApproval(id, request, userId));
    }

    @Operation(summary = "Thu hồi yêu cầu duyệt (UC11)",
            description = "Người soạn thảo thu hồi văn bản đã gửi duyệt khi người duyệt chưa xử lý. Trạng thái chuyển về lại DRAFT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thu hồi thành công"),
            @ApiResponse(responseCode = "400", description = "Không thể thu hồi do người duyệt đã xử lý (DOCUMENT_CANNOT_RECALL)")
    })
    @PostMapping("/{id}/recall-approval")
    public BaseResponse<DocumentResponse> recallApproval(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) RecallApprovalRequest request,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        return BaseResponse.success(documentApprovalService.recallApproval(id, request, userId));
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserPrincipal principal)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Người dùng chưa được xác thực hoặc thông tin phiên đăng nhập không hợp lệ");
        }
        return principal.getUserId();
    }
}
