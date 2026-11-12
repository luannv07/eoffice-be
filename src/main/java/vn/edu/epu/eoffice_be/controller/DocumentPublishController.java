package vn.edu.epu.eoffice_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.edu.epu.eoffice_be.dto.IntegrityVerifyResponse;
import vn.edu.epu.eoffice_be.dto.PublishRequest;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.service.DocumentPublishService;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Document Publish", description = "Phát hành văn bản và kiểm tra tính toàn vẹn (UC15 - UC17)")
public class DocumentPublishController {

    private final DocumentPublishService publishService;

    // UC15: Phát hành văn bản
    @Deprecated(since = "2026")
    @Operation(
            summary = "Phát hành văn bản (Khuyến nghị chuyển sang /workflow/sign-and-publish)",
            description = "Endpoint phát hành trực tiếp. Khuyến nghị sử dụng POST /documents/{id}/workflow/sign-and-publish "
                    + "để kết hợp ký số PKI và cấp số từ Sổ văn bản an toàn với Pessimistic Lock."
    )
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN', 'LEADER', 'CLERK') or hasAuthority('DOCUMENT_PUBLISH')")
    public ResponseEntity<DocumentResponse> publishDocument(
            @PathVariable Long id,
            @Valid @RequestBody PublishRequest request) {
        DocumentResponse response = publishService.publishDocumentResponse(id, request);
        return ResponseEntity.ok(response);
    }


    // UC17: Kiểm tra tính toàn vẹn văn bản
    @GetMapping("/{id}/verify-integrity")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<IntegrityVerifyResponse> verifyIntegrity(
            @PathVariable Long id,
            org.springframework.security.core.Authentication authentication) {
        if (authentication == null 
                || !authentication.isAuthenticated() 
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            throw new org.springframework.security.access.AccessDeniedException("Yêu cầu xác thực người dùng");
        }
        boolean isValid = publishService.verifyIntegrity(id, authentication);
        IntegrityVerifyResponse response = IntegrityVerifyResponse.builder()
                .documentId(id)
                .valid(isValid)
                .build();
        return ResponseEntity.ok(response);
    }
}
