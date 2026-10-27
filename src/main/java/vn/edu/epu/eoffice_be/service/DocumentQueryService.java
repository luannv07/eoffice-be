package vn.edu.epu.eoffice_be.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.epu.eoffice_be.common.enums.DocumentSecurityLevel;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository.DrafterName;
import vn.edu.epu.eoffice_be.repository.DocumentDistributionRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentQueryService {

    static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");

    DocumentRepository documentRepository;
    DocumentDistributionRepository distributionRepository;
    DocumentPdfStorageService documentPdfStorageService;
    vn.edu.epu.eoffice_be.repository.UserInfoRepository userInfoRepository;

    @Transactional(readOnly = true)
    public Object getDocumentDetails(Long id, Long orgId) {
        Document document = documentRepository.findAccessiblePublishedDocument(id, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hoặc không có quyền truy cập văn bản với ID: " + id));

        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("id", document.getId());
        details.put("title", document.getTitle());
        details.put("status", document.getStatus());
        details.put("orgId", orgId);
        details.put("updatedAt", document.getUpdatedDate());
        details.put("drafterId", document.getDrafter() != null ? document.getDrafter().getId() : null);
        details.put("drafterUsername", document.getDrafter() != null ? document.getDrafter().getUsername() : null);
        details.put("drafterFullName", drafterFullName(document));
        return details;
    }

    @Transactional(readOnly = true)
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
        details.put("drafterId", document.getDrafter() != null ? document.getDrafter().getId() : null);
        details.put("drafterUsername", document.getDrafter() != null ? document.getDrafter().getUsername() : null);
        details.put("drafterFullName", drafterFullName(document));
        return details;
    }

    @PreAuthorize("hasAnyRole('LEADER', 'CLERK', 'ADMIN', 'SYSTEM_ADMIN')")
    @Transactional(readOnly = true)
    public Object getDocumentDetailsForStaff(Long id, Long orgId) {
        if (id == null) {
            throw new IllegalArgumentException("ID văn bản không được để trống");
        }
        Document document = documentRepository.findByIdAndDeleteFlgFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy văn bản với ID: " + id));

        validateStaffAccess(document, orgId);

        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("id", document.getId());
        details.put("title", document.getTitle());
        details.put("status", document.getStatus());
        details.put("orgId", orgId);
        details.put("updatedAt", document.getUpdatedDate());
        details.put("drafterId", document.getDrafter() != null ? document.getDrafter().getId() : null);
        details.put("drafterUsername", document.getDrafter() != null ? document.getDrafter().getUsername() : null);
        details.put("drafterFullName", drafterFullName(document));
        return details;
    }

    @Transactional(readOnly = true)
    public Object getDocumentDetailsForStaff(Long id) {
        return getDocumentDetailsForStaff(id, null);
    }

    public byte[] downloadDocument(Long documentId, Long orgId) {
        Document doc = documentRepository.findAccessiblePublishedDocument(documentId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hoặc không có quyền truy cập văn bản với ID: " + documentId));

        return readDocumentFileContent(doc);
    }

    public byte[] downloadDocumentForAdmin(Long documentId) {
        Document doc = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .filter(d -> d.getStatus() == DocumentStatus.PUBLISHED || d.getStatus() == DocumentStatus.ISSUED)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hoặc không có quyền tải văn bản: " + documentId));
        return readDocumentFileContent(doc);
    }

    @PreAuthorize("hasAnyRole('LEADER', 'CLERK', 'ADMIN', 'SYSTEM_ADMIN')")
    public byte[] downloadDocumentForStaff(Long documentId, Long orgId) {
        if (documentId == null) {
            throw new IllegalArgumentException("ID văn bản không được để trống");
        }
        Document doc = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy văn bản với ID: " + documentId));

        validateStaffAccess(doc, orgId);
        return readDocumentFileContent(doc);
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
     * 3. Với các văn bản phát hành thông thường, kiểm tra đơn vị (orgId): Nếu văn bản thuộc đơn vị cụ thể
     *    và người dùng có orgId khác (không phải 0L/SYSTEM_WIDE), quyền truy cập sẽ bị từ chối.
     * 4. Với các văn bản cấp trường hoặc khi orgId là SYSTEM_WIDE_ORG_ID (0L) / null, LEADER và CLERK có thẩm quyền
     *    tra cứu và tải trên toàn hệ thống theo quy định chỉ đạo và quản lý văn thư của trường.
     * </p>
     *
     * @param doc   Tài liệu cần kiểm tra
     * @param orgId Mã đơn vị của người dùng (nếu có)
     * @return true nếu được phép truy cập, ngược lại false
     */
    public boolean isStaffAuthorizedForDocument(Document doc, Long orgId) {
        if (doc == null) {
            return false;
        }
        try {
            validateStaffAccess(doc, orgId);
            return true;
        } catch (AccessDeniedException e) {
            return false;
        }
    }

    private void validateStaffAccess(Document doc, Long orgId) {
        if (doc == null || doc.getStatus() != DocumentStatus.PUBLISHED) {
            throw new AccessDeniedException("Bạn không có quyền truy cập văn bản chưa ban hành");
        }
        if (doc.getSecurityLevel() == DocumentSecurityLevel.SECRET) {
            throw new AccessDeniedException("Văn bản tuyệt mật chỉ dành riêng cho Quản trị viên");
        }
        Long docOrgId = doc.getOrgId();
        if (docOrgId != null && orgId != null && !orgId.equals(0L) && !orgId.equals(docOrgId)) {
            throw new AccessDeniedException("Văn bản thuộc đơn vị khác, bạn không có quyền truy cập");
        }
    }

    private byte[] readDocumentFileContent(Document doc) {
        return documentPdfStorageService.readPdf(doc.getOfficialFilePath());
    }

    // 1. Tra cứu danh sách văn bản theo quyền hạn của phòng ban (hoặc tất cả nếu orgId == null)
    @Transactional(readOnly = true)
    public List<DocumentResponse> getPublishedDocuments(Long orgId) {
        return getPublishedDocuments(orgId, null);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getPublishedDocuments(Long orgId, Long documentBookId) {
        if (orgId == null) {
            return getAllPublishedDocuments(documentBookId);
        }
        List<Document> docs = (documentBookId != null)
                ? documentRepository.findPublishedDocumentsForUser(orgId, null, documentBookId)
                : documentRepository.findPublishedDocumentsForUser(orgId);
        return mapDocumentList(docs);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getAllPublishedDocuments() {
        return getAllPublishedDocuments(null);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getAllPublishedDocuments(Long documentBookId) {
        List<Document> docs = (documentBookId != null)
                ? documentRepository.findByStatusOrderByIssueDateDescIdDesc(
                        vn.edu.epu.eoffice_be.common.enums.DocumentStatus.PUBLISHED,
                        documentBookId)
                : documentRepository.findByStatusOrderByIssueDateDescIdDesc(
                        vn.edu.epu.eoffice_be.common.enums.DocumentStatus.PUBLISHED);
        return mapDocumentList(docs);
    }

    private List<DocumentResponse> mapDocumentList(List<Document> docs) {
        Set<Long> drafterIds = docs.stream().map(Document::getDrafter)
                .filter(Objects::nonNull).map(UserLogin::getId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, DrafterName> names = drafterIds.isEmpty() ? Map.of()
                : userInfoRepository.findDrafterNamesByUserIds(drafterIds).stream()
                        .collect(Collectors.toMap(DrafterName::getUserId, name -> name));
        return docs.stream().map(doc -> mapToDocumentResponse(doc, names)).toList();
    }

    private String drafterFullName(Document document) {
        if (document.getDrafter() == null) return null;
        return userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(document.getDrafter().getId())
                .map(vn.edu.epu.eoffice_be.entity.UserInfo::getFullName).orElse(document.getDrafter().getUsername());
    }

    private DocumentResponse mapToDocumentResponse(Document doc, Map<Long, DrafterName> names) {
        UserLogin drafter = doc.getDrafter();
        Long drafterId = drafter != null ? drafter.getId() : null;
        DrafterName name = drafterId != null ? names.get(drafterId) : null;
        String username = name != null ? name.getUsername() : drafter != null ? drafter.getUsername() : null;
        String fullName = name != null && name.getFullName() != null ? name.getFullName() : username;
        return DocumentResponse.builder()
                .id(doc.getId())
                .drafterId(drafterId)
                .drafterUsername(username)
                .drafterFullName(fullName)
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
                .drafterId(doc.getDrafter() != null ? doc.getDrafter().getId() : null)
                .drafterUsername(doc.getDrafter() != null ? doc.getDrafter().getUsername() : null)
                .drafterFullName(drafterFullName(doc))
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

        Resource resource = documentPdfStorageService.readAsResource(doc.getOfficialFilePath());
        log.info("User: {} | IP: {} | DocumentId: {} | Time: {}", username, clientIp, documentId, LocalDate.now(ZONE_VN));
        return resource;
    }
}
