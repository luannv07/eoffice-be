package vn.edu.epu.eoffice_be.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.edu.epu.eoffice_be.dto.IntegrityVerifyResponse;
import vn.edu.epu.eoffice_be.dto.PublishRequest;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.service.DocumentPublishService;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("isAuthenticated()")
public class DocumentPublishController {

    DocumentPublishService publishService;

    // UC15: Phát hành văn bản
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN', 'LEADER', 'CLERK') or hasAuthority('DOCUMENT_PUBLISH')")
    public ResponseEntity<DocumentResponse> publishDocument(
            @PathVariable Long id,
            @Valid @RequestBody PublishRequest request) {
        Document doc = publishService.publishDocument(id, request);
        DocumentResponse response = DocumentResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .officialNumber(doc.getOfficialNumber())
                .status(doc.getStatus())
                .issueDate(doc.getIssueDate())
                .effectiveDate(doc.getEffectiveDate())
                .officialFilePath(doc.getOfficialFilePath())
                .officialHash(doc.getOfficialHash())
                .build();
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<IntegrityVerifyResponse> verifyIntegrity(Long id) {
        boolean isValid = publishService.verifyIntegrity(id);
        IntegrityVerifyResponse response = IntegrityVerifyResponse.builder()
                .documentId(id)
                .valid(isValid)
                .build();
        return ResponseEntity.ok(response);
    }

    // UC17: Kiểm tra tính toàn vẹn văn bản
    @GetMapping("/{id}/verify-integrity")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<IntegrityVerifyResponse> verifyIntegrity(
            @PathVariable Long id,
            org.springframework.security.core.Authentication authentication) {
        boolean isValid = publishService.verifyIntegrity(id, authentication);
        IntegrityVerifyResponse response = IntegrityVerifyResponse.builder()
                .documentId(id)
                .valid(isValid)
                .build();
        return ResponseEntity.ok(response);
    }
}