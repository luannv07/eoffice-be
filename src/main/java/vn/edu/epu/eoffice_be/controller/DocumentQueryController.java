package vn.edu.epu.eoffice_be.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.epu.eoffice_be.service.DocumentQueryService;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("isAuthenticated()")
public class DocumentQueryController {

    DocumentQueryService queryService;

    private Long resolveUserOrgId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Yêu cầu xác thực người dùng");
        }
        Object principal = authentication.getPrincipal();
        try {
            var method = principal.getClass().getMethod("getOrgId");
            method.setAccessible(true);
            Object val = method.invoke(principal);
            if (val instanceof Long longVal && longVal != null) {
                return longVal;
            }
        } catch (Exception e) {
            throw new org.springframework.security.access.AccessDeniedException("Không thể xác định đơn vị (orgId) hợp lệ của người dùng", e);
        }
        throw new org.springframework.security.access.AccessDeniedException("Người dùng không có thông tin đơn vị (orgId) hợp lệ");
    }

    // UC16.1: Tra cứu danh sách văn bản ban hành
    @GetMapping("/published")
    public ResponseEntity<Object> getPublishedDocuments(Authentication authentication) {
        Long orgId = resolveUserOrgId(authentication);
        return ResponseEntity.ok(queryService.getPublishedDocuments(orgId));
    }

    // UC16.2: Xem chi tiết trích yếu
    @GetMapping("/{id}/details")
    public ResponseEntity<Object> getDocumentDetails(@PathVariable Long id, Authentication authentication) {
        Long orgId = resolveUserOrgId(authentication);
        return ResponseEntity.ok(queryService.getDocumentDetails(id, orgId));
    }

    // UC16.3: Tải file PDF chính thức
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id, Authentication authentication) {
        Long orgId = resolveUserOrgId(authentication);
        byte[] pdfBytes = queryService.downloadDocument(id, orgId);
        ByteArrayResource resource = new ByteArrayResource(pdfBytes);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document_" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfBytes.length)
                .body(resource);
    }
}