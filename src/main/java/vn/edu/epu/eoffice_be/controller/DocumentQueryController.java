package vn.edu.epu.eoffice_be.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.epu.eoffice_be.service.DocumentQueryService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentQueryController {

    DocumentQueryService queryService;

    // UC16.1: Tra cứu danh sách văn bản ban hành (truyền orgId của đơn vị hiện tại)
    @GetMapping("/published")
    public ResponseEntity<Object> getPublishedDocuments(Authentication authentication) {
        // Nếu có authentication, lấy orgId từ user details, ngược lại dùng ID mặc định nội bộ
        Long orgId = 1L; // Hoặc resolve từ authentication: ((CustomUserDetails) authentication.getPrincipal()).getOrgId();
        return ResponseEntity.ok(queryService.getPublishedDocuments(orgId));
    }

    // UC16.2: Xem chi tiết trích yếu, số hiệu, ngày ban hành
    @GetMapping("/{id}/details")
    public ResponseEntity<Object> getDocumentDetails(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "1") Long orgId) {
        return ResponseEntity.status(HttpStatus.OK).body(queryService.getDocumentDetails(id, orgId));
    }

    // UC16.3: Tải file PDF chính thức đã ký số
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable("id") Long id,
            Authentication authentication) {
        Long orgId = 1L; 
        byte[] pdfBytes = queryService.downloadDocument(id, orgId);
        ByteArrayResource resource = new ByteArrayResource(pdfBytes);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document_" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfBytes.length)
                .body(resource);
    }
}