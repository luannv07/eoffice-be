package vn.edu.epu.eoffice_be.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.epu.eoffice_be.common.enums.DocumentSecurityLevel;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.repository.DocumentDistributionRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentQueryService {

    static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");

    DocumentRepository documentRepository;
    DocumentDistributionRepository distributionRepository;

    public Object getDocumentDetails(Long id, Long orgId) {
        Document document = documentRepository.findAccessiblePublishedDocument(id, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hoặc không có quyền truy cập văn bản với ID: " + id));

        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("id", document.getId());
        details.put("title", document.getTitle());
        details.put("status", document.getStatus());
        details.put("orgId", orgId);
        details.put("updatedAt", document.getUpdatedDate());
        return details;
    }

    public Object getDocumentDetailsForAdmin(Long id) {
        Document document = documentRepository.findByIdAndDeleteFlgFalse(id)
                .filter(d -> d.getStatus() == DocumentStatus.PUBLISHED || d.getStatus() == DocumentStatus.ISSUED)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hoặc không có quyền truy cập văn bản với ID: " + id));

        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("id", document.getId());
        details.put("title", document.getTitle());
        details.put("status", document.getStatus());
        details.put("orgId", null);
        details.put("updatedAt", document.getUpdatedDate());
        return details;
    }

    @PreAuthorize("hasAnyRole('LEADER', 'CLERK', 'ADMIN', 'SYSTEM_ADMIN')")
    public Object getDocumentDetailsForStaff(Long id, Long orgId) {
        Document document = documentRepository.findByIdAndDeleteFlgFalse(id)
                .filter(d -> d.getStatus() == DocumentStatus.PUBLISHED)
                .filter(d -> isStaffAuthorizedForDocument(d, orgId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hoặc không có quyền truy cập văn bản với ID: " + id));

        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("id", document.getId());
        details.put("title", document.getTitle());
        details.put("status", document.getStatus());
        details.put("orgId", orgId);
        details.put("updatedAt", document.getUpdatedDate());
        return details;
    }

    public Object getDocumentDetailsForStaff(Long id) {
        return getDocumentDetailsForStaff(id, null);
    }

    public byte[] downloadDocument(Long documentId, Long orgId) {
        Document doc = documentRepository.findAccessiblePublishedDocument(documentId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hoặc không có quyền truy cập văn bản với ID: " + documentId));

        return readDocumentFileContent(doc, documentId);
    }

    public byte[] downloadDocumentForAdmin(Long documentId) {
        Document doc = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .filter(d -> d.getStatus() == DocumentStatus.PUBLISHED || d.getStatus() == DocumentStatus.ISSUED)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hoặc không có quyền tải văn bản: " + documentId));
        return readDocumentFileContent(doc, documentId);
    }

    @PreAuthorize("hasAnyRole('LEADER', 'CLERK', 'ADMIN', 'SYSTEM_ADMIN')")
    public byte[] downloadDocumentForStaff(Long documentId, Long orgId) {
        Document doc = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .filter(d -> d.getStatus() == DocumentStatus.PUBLISHED)
                .filter(d -> isStaffAuthorizedForDocument(d, orgId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hoặc không có quyền tải văn bản: " + documentId));
        return readDocumentFileContent(doc, documentId);
    }

    public byte[] downloadDocumentForStaff(Long documentId) {
        return downloadDocumentForStaff(documentId, null);
    }

    /**
     * Xác thực quyền hạn truy cập văn bản cho vai trò nhân viên đặc quyền (LEADER/CLERK).
     * <p>
     * Business Rule (UC-16 / Policy Decision):
     * 1. Văn bản phải ở trạng thái PUBLISHED và chưa bị xóa mềm.
     * 2. Văn bản có mức độ bảo mật TUYỆT MẬT (SECRET) chỉ dành riêng cho Quản trị viên (ADMIN);
     *    các vai trò nhân viên / lãnh đạo đơn vị không được phép truy cập để bảo đảm an toàn dữ liệu nhạy cảm.
     * 3. Với các văn bản phát hành thông thường hoặc bảo mật khác, LEADER và CLERK có thẩm quyền
     *    tra cứu và tải trên toàn hệ thống theo quy định chỉ đạo và quản lý văn thư của trường.
     * </p>
     *
     * @param doc   Tài liệu cần kiểm tra
     * @param orgId Mã đơn vị của người dùng (nếu có)
     * @return true nếu được phép truy cập, ngược lại false
     */
    public boolean isStaffAuthorizedForDocument(Document doc, Long orgId) {
        if (doc == null || doc.getStatus() != DocumentStatus.PUBLISHED) {
            return false;
        }
        if (doc.getSecurityLevel() == DocumentSecurityLevel.SECRET) {
            return false;
        }
        return true;
    }

    private byte[] readDocumentFileContent(Document doc, Long documentId) {
        String rawPath = doc.getOfficialFilePath();
        if (rawPath == null || rawPath.isBlank()) {
            return ("PDF_MOCK_CONTENT_" + documentId).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        // Chống Path Traversal
        if (rawPath.contains("..")) {
            throw new SecurityException("Phát hiện nguy cơ Path Traversal trong đường dẫn tệp!");
        }

        java.nio.file.Path resolvedPath = java.nio.file.Paths.get(rawPath).normalize();
        java.io.File file = resolvedPath.toFile();

        if (file.exists() && file.isFile()) {
            try {
                return java.nio.file.Files.readAllBytes(resolvedPath);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Không thể đọc tệp văn bản", e);
            }
        }

        return ("PDF_MOCK_CONTENT_" + documentId).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // 1. Tra cứu danh sách văn bản theo quyền hạn của phòng ban (hoặc tất cả nếu orgId == null)
    public List<DocumentResponse> getPublishedDocuments(Long orgId) {
        return getPublishedDocuments(orgId, null);
    }

    public List<DocumentResponse> getPublishedDocuments(Long orgId, Long documentBookId) {
        if (orgId == null) {
            return getAllPublishedDocuments(documentBookId);
        }
        List<Document> docs = (documentBookId != null)
                ? documentRepository.findPublishedDocumentsForUser(orgId, null, documentBookId)
                : documentRepository.findPublishedDocumentsForUser(orgId);
        return docs.stream()
                .<DocumentResponse>map(this::mapToDocumentResponse)
                .toList();
    }

    public List<DocumentResponse> getAllPublishedDocuments() {
        return getAllPublishedDocuments(null);
    }

    public List<DocumentResponse> getAllPublishedDocuments(Long documentBookId) {
        List<Document> docs = (documentBookId != null)
                ? documentRepository.findByStatusOrderByIssueDateDescIdDesc(
                        vn.edu.epu.eoffice_be.common.enums.DocumentStatus.PUBLISHED,
                        documentBookId)
                : documentRepository.findByStatusOrderByIssueDateDescIdDesc(
                        vn.edu.epu.eoffice_be.common.enums.DocumentStatus.PUBLISHED);
        return docs.stream()
                .<DocumentResponse>map(this::mapToDocumentResponse)
                .toList();
    }

    private DocumentResponse mapToDocumentResponse(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .officialNumber(doc.getOfficialNumber())
                .documentBookId(doc.getDocumentBook() != null ? doc.getDocumentBook().getId() : null)
                .documentBookCode(doc.getDocumentBook() != null ? doc.getDocumentBook().getBookCode() : null)
                .status(doc.getStatus())
                .statusDescription(doc.getStatus() != null ? doc.getStatus().getDescription() : null)
                .issueDate(doc.getIssueDate() != null ? LocalDate.ofInstant(doc.getIssueDate(), ZONE_VN) : null)
                .effectiveDate(doc.getEffectiveDate() != null ? LocalDate.ofInstant(doc.getEffectiveDate(), ZONE_VN) : null)
                .officialHash(doc.getOfficialHash())
                .build();
    }

    // 2. Xem chi tiết văn bản và tự động đánh dấu đã đọc
    @Transactional
    public DocumentResponse getDocumentDetail(Long documentId, Long orgId) {
        Document doc = documentRepository.findAccessiblePublishedDocument(documentId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Bạn không có quyền xem hoặc văn bản không tồn tại"));

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
                .statusDescription(doc.getStatus() != null ? doc.getStatus().getDescription() : null)
                .issueDate(doc.getIssueDate() != null ? LocalDate.ofInstant(doc.getIssueDate(), ZONE_VN) : null)
                .effectiveDate(doc.getEffectiveDate() != null ? LocalDate.ofInstant(doc.getEffectiveDate(), ZONE_VN) : null)
                .officialHash(doc.getOfficialHash())
                .build();
    }

    // 3. Tải file PDF chính thức đã ký số kèm ghi nhận Audit Log
    public Resource downloadOfficialFile(Long documentId, Long orgId, String username, String clientIp) {
        Document doc = documentRepository.findAccessiblePublishedDocument(documentId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Bạn không có quyền tải văn bản này"));

        if (doc.getOfficialFilePath() == null || doc.getOfficialFilePath().isBlank()) {
            throw new IllegalArgumentException("Không tìm thấy dữ liệu yêu cầu");
        }

        try {
            Path filePath = Paths.get(doc.getOfficialFilePath());
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("Thông tin yêu cầu không hợp lệ hoặc không tồn tại");
            }

            log.info("Thực hiện xử lý truy vấn văn bản");
            log.info("User: {} | IP: {} | DocumentId: {} | Time: {}", username, clientIp, documentId, LocalDate.now(ZONE_VN));

            return resource;
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("Đường dẫn tệp không hợp lệ", ex);
        }
    }
}
