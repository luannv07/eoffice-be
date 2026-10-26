package vn.edu.epu.eoffice_be.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.edu.epu.eoffice_be.dto.IntegrityVerifyResponse;
import vn.edu.epu.eoffice_be.service.DocumentPublishService;


@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("isAuthenticated()")
@Tag(name = "Document Integrity", description = "Kiểm tra tính toàn vẹn văn bản đã ban hành (UC17)")
public class DocumentPublishController {

    DocumentPublishService publishService;


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