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

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentQueryController {

    DocumentQueryService queryService;

    // UC16.1: Tra cứu danh sách văn bản ban hành (truyền orgId của đơn vị hiện tại)
    @GetMapping("/published")
    public ResponseEntity<Object> getPublishedDocuments(
            @RequestParam(required = false, defaultValue = "1") Long orgId) {
        return ResponseEntity.status(HttpStatus.OK).body(queryService.getPublishedDocuments(orgId));
    }

    // UC16.2: Xem chi tiết trích yếu, số hiệu, ngày ban hành
    @GetMapping("/{id}/details")
    public ResponseEntity<Object> getDocumentDetails(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "1") Long orgId) {
        return ResponseEntity.status(HttpStatus.OK).body(queryService.getDocumentDetails(id, orgId));
    }

    // UC16.3: Tải file PDF chính thức đã ký số
    // UC16.3: Tải file PDF chính thức đã ký số
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable("id") Long id,
            @RequestParam(name = "orgId", required = false, defaultValue = "1") Long orgId) {
        byte[] pdfBytes = queryService.downloadDocument(id, orgId);

        if (pdfBytes == null || pdfBytes.length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("van_ban_" + id + "_signed.pdf")
                .build());

        return ResponseEntity.status(HttpStatus.OK)
                .headers(headers)
                .body(pdfBytes);
    }
}