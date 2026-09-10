package vn.edu.epu.eoffice_be.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.epu.eoffice_be.dto.DocumentResponse;
import vn.edu.epu.eoffice_be.service.DocumentQueryService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
private static final String OFFICIAL_HASH_KEY = "officialHash";
public class DocumentQueryController {

    DocumentQueryService queryService;

    // UC16.1: Tra cứu danh sách văn bản ban hành (truyền orgId của đơn vị hiện tại)
    @GetMapping("/published")
    public ResponseEntity<?> getPublishedDocuments(
            @RequestParam(required = false, defaultValue = "1") Long orgId) {
        
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();

        java.util.Map<String, Object> doc1 = new java.util.LinkedHashMap<>();
        doc1.put("id", 1L);
        doc1.put("title", "Quyết định ban hành quy chế văn thư lưu trữ điện tử");
        doc1.put("officialNumber", "01/QĐ-ĐHL");
        doc1.put("status", "PUBLISHED");
        doc1.put("issueDate", java.time.LocalDate.now().minusDays(1));
        doc1.put("effectiveDate", java.time.LocalDate.now());
        doc1.put("signer", "Ban Giám hiệu");
        doc1.put("officialFilePath", "uploads/documents/doc_1_official.pdf");
        doc1.put("OFFICIAL_HASH_KEY", "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e");
        list.add(doc1);

        java.util.Map<String, Object> doc2 = new java.util.LinkedHashMap<>();
        doc2.put("id", 2L);
        doc2.put("title", "Thông báo lịch công tác tuần và triển khai nhiệm vụ quý IV");
        doc2.put("officialNumber", "02/TB-ĐHL");
        doc2.put("status", "PUBLISHED");
        doc2.put("issueDate", java.time.LocalDate.now());
        doc2.put("effectiveDate", java.time.LocalDate.now());
        doc2.put("signer", "Văn phòng Đoàn trường");
        doc2.put("officialFilePath", "uploads/documents/doc_2_official.pdf");
        doc2.put("OFFICIAL_HASH_KEY", "b781a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f9999");
        list.add(doc2);

        return ResponseEntity.ok(list);
    }

    // UC16.2: Xem chi tiết trích yếu, số hiệu, ngày ban hành
    @GetMapping("/{id}/details")
    public ResponseEntity<Object> getDocumentDetails(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "1") Long orgId) {
        
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("id", id);
        details.put("title", "Quy chế văn thư lưu trữ điện tử");
        details.put("officialNumber", "0" + id + "/QĐ-ĐHL");
        details.put("status", "PUBLISHED");
        details.put("issueDate", java.time.LocalDate.now());
        details.put("effectiveDate", java.time.LocalDate.now().plusDays(1));
        details.put("signer", "Ban Giám hiệu");
        details.put("summary", "Quy định về việc tạo lập, phát hành, lưu trữ và theo dõi văn bản điện tử tại cơ quan.");
        details.put("officialFilePath", "uploads/documents/doc_" + id + "_official.pdf");
        details.put("OFFICIAL_HASH_KEY", "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e");
        details.put("isRead", true);
        details.put("readAt", java.time.Instant.now());

        return ResponseEntity.ok(details);
    }

    // UC16.3: Tải file PDF chính thức đã ký số
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "1") Long orgId) {
        try {
            // Nội dung tệp PDF mẫu đạt chuẩn cấu trúc byte PDF
            String dummyPdfContent = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Count 1/Kids[3 0 R]>>endobj\n3 0 obj<</Type/Page/MediaBox[0 0 612 792]/Parent 2 0 R/Resources<<>>>>endobj\nxref\n0 4\n0000000000 65535 f\n0000000009 00000 n\n0000000052 00000 n\n0000000101 00000 n\ntrailer<</Size 4/Root 1 0 R>>\nstartxref\n178\n%%EOF";
            byte[] pdfBytes = dummyPdfContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
            headers.setContentDisposition(org.springframework.http.ContentDisposition.attachment()
                    .filename("van_ban_" + id + "_signed.pdf")
                    .build());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}