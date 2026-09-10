package vn.edu.epu.eoffice_be.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentDistribution;
import vn.edu.epu.eoffice_be.repository.DocumentDistributionRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentQueryService {

    DocumentRepository documentRepository;
    DocumentDistributionRepository distributionRepository;
    public Object getDocumentDetails(Long id, Long orgId) {
        var document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy văn bản với ID: " + id));

        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("id", document.getId());
        details.put("title", document.getTitle());
        details.put("status", document.getStatus());
        details.put("orgId", orgId);
        details.put("updatedAt", document.getUpdatedDate());
        return details;
    }

    public byte[] downloadDocument(Long id, Long orgId) {
        var document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy văn bản với ID: " + id));

        String content = String.format(
                "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM%n"
                + "Độc lập - Tự do - Hạnh phúc%n%n"
                + "THÔNG TIN VĂN BẢN ĐIỆN TỬ%n"
                + "Mã hệ thống: %d%n"
                + "Tiêu đề: %s%n"
                + "Trạng thái: %s%n"
                + "Mã đơn vị tiếp nhận: %d%n",
                document.getId(),
                document.getTitle(),
                document.getStatus(),
                orgId != null ? orgId : 0L
        );

        return content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    // 1. Tra cứu danh sách văn bản theo quyền hạn của phòng ban
    public List<DocumentResponse> getPublishedDocuments(Long orgId) {
        return documentRepository.findPublishedDocumentsForUser(orgId).stream()
                .map(doc -> DocumentResponse.builder()
                        .id(doc.getId())
                        .title(doc.getTitle())
                        .officialNumber(doc.getOfficialNumber())
                        .status(doc.getStatus())
                        .issueDate(doc.getIssueDate())
                        .effectiveDate(doc.getEffectiveDate())
                        .officialHash(doc.getOfficialHash())
                        .build())
                .toList();
    }

    // 2. Xem chi tiết văn bản và tự động đánh dấu đã đọc
    @Transactional
    public DocumentResponse getDocumentDetail(Long documentId, Long orgId) {
        Document doc = documentRepository.findAccessiblePublishedDocument(documentId, orgId)
                .orElseThrow(() -> new RuntimeException("Bạn không có quyền xem hoặc văn bản không tồn tại"));

        distributionRepository.findByDocumentIdAndTargetId(documentId, orgId).ifPresent(dist -> {
            if (Boolean.FALSE.equals(dist.getIsRead())) {
                dist.setIsRead(true);
                dist.setReadAt(Instant.now());
                distributionRepository.save(dist);
            }
        });

        return DocumentResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .officialNumber(doc.getOfficialNumber())
                .status(doc.getStatus())
                .issueDate(doc.getIssueDate())
                .effectiveDate(doc.getEffectiveDate())
                .officialHash(doc.getOfficialHash())
                .build();
    }

    // 3. Tải file PDF chính thức đã ký số kèm ghi nhận Audit Log
    public Resource downloadOfficialFile(Long documentId, Long orgId, String username, String clientIp) {
        Document doc = documentRepository.findAccessiblePublishedDocument(documentId, orgId)
                .orElseThrow(() -> new RuntimeException("Bạn không có quyền tải văn bản này"));

        if (doc.getOfficialFilePath() == null || doc.getOfficialFilePath().isBlank()) {
            throw new RuntimeException("Văn bản chưa có tệp đính kèm chính thức");
        }

        try {
            Path filePath = Paths.get(doc.getOfficialFilePath());
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new RuntimeException("Tệp tin không tồn tại trên hệ thống lưu trữ");
            }

            // Ghi nhận Audit Log tải tệp (In ra console log hệ thống)
            System.out.printf("[AUDIT LOG] Action: DOWNLOAD | User: %s | IP: %s | DocumentId: %d | Time: %s%n",
                    username, clientIp, documentId, LocalDateTime.now());

            return resource;
        } catch (Exception ex) {
            throw new RuntimeException("Lỗi đọc tệp: " + ex.getMessage());
        }
    }
}