package vn.edu.epu.eoffice_be.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.PublishRequest;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentDistribution;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository;
import vn.edu.epu.eoffice_be.repository.DocumentDistributionRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import vn.edu.epu.eoffice_be.dto.request.AssignDocumentNumberRequest;
import vn.edu.epu.eoffice_be.dto.response.AssignDocumentNumberResponse;

@Slf4j
@Service
public class DocumentPublishService {

    private final DocumentRepository documentRepository;
    private final DocumentDistributionRepository distributionRepository;
    private final DocumentBookService documentBookService;
    private final DocumentPdfStorageService documentPdfStorageService;
    private final UserInfoRepository userInfoRepository;

    @Autowired
    public DocumentPublishService(DocumentRepository documentRepository,
                                  DocumentDistributionRepository distributionRepository,
                                  @Nullable DocumentBookService documentBookService,
                                  DocumentPdfStorageService documentPdfStorageService,
                                  UserInfoRepository userInfoRepository) {
        this.documentRepository = documentRepository;
        this.distributionRepository = distributionRepository;
        this.documentBookService = documentBookService;
        this.documentPdfStorageService = Objects.requireNonNull(
                documentPdfStorageService, "documentPdfStorageService must not be null");
        this.userInfoRepository = Objects.requireNonNull(userInfoRepository, "userInfoRepository must not be null");
    }
    
    /** Publish and resolve the response while the service transaction is still open. */
    @Transactional
    public DocumentResponse publishDocumentResponse(Long documentId, PublishRequest request) {
        return toPublishResponse(publishDocument(documentId, request));
    }

    private DocumentResponse toPublishResponse(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .drafterId(doc.getDrafter() != null ? doc.getDrafter().getId() : null)
                .drafterUsername(doc.getDrafter() != null ? doc.getDrafter().getUsername() : null)
                .drafterFullName(doc.getDrafter() != null ? userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(doc.getDrafter().getId())
                        .map(vn.edu.epu.eoffice_be.entity.UserInfo::getFullName).orElse(doc.getDrafter().getUsername()) : null)
                .title(doc.getTitle())
                .officialNumber(doc.getOfficialNumber())
                .status(doc.getStatus())
                .statusDescription(doc.getStatus() != null ? doc.getStatus().getDescription() : null)
                .issueDate(doc.getIssueDate() != null ? LocalDate.ofInstant(doc.getIssueDate(), Document.getBusinessZone()) : null)
                .effectiveDate(doc.getEffectiveDate() != null ? LocalDate.ofInstant(doc.getEffectiveDate(), Document.getBusinessZone()) : null)
                .officialFilePath(doc.getOfficialFilePath())
                .officialHash(doc.getOfficialHash())
                .build();
    }

    @Transactional
    public Document publishDocument(Long documentId, PublishRequest request) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND,"Không tìm thấy văn bản có id"+ documentId));
        
        if(Boolean.TRUE.equals(doc.isDeleteFlg())){
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "Văn bản đã bị xoá");
        }
        Set<DocumentStatus> validStatuses = Set.of(
            DocumentStatus.APPROVED, 
            DocumentStatus.DRAFT
        );
        if (doc.getStatus() != null && !validStatuses.contains(doc.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATUS, 
                    "Chỉ có thể ban hành văn bản đã được phê duyệt");
        }
        String computedHash = calculateSha256(doc.getOfficialFilePath());
        applyDocumentMetadata(doc, request,computedHash);

        Document savedDoc = documentRepository.save(doc);
        distributeDocument(savedDoc.getId(), request);

        log.info("Ban hành văn bản thành công: id={}, số hiệu={}", 
            savedDoc.getId(), savedDoc.getOfficialNumber());

        // Preserve initialized author data for legacy callers that still receive an entity.
        org.hibernate.Hibernate.initialize(savedDoc.getDrafter());
        return savedDoc;
    }

    private void applyDocumentMetadata(Document doc, PublishRequest request, String computedHash) {
        if (request != null && request.getDocumentBookId() != null && documentBookService != null) {
            AssignDocumentNumberRequest assignReq = new AssignDocumentNumberRequest();
            assignReq.setBookId(request.getDocumentBookId());
            assignReq.setDocumentId(doc.getId());
            assignReq.setEffectiveDate(request.getEffectiveDate() != null ? request.getEffectiveDate() : LocalDate.now(Document.getBusinessZone()));
            AssignDocumentNumberResponse numberResp = documentBookService.assignNumber(assignReq);
            doc.setOfficialNumber(numberResp.getDocumentNumber());
        } else if (doc.getOfficialNumber() == null || doc.getOfficialNumber().isBlank()) {
            doc.setOfficialNumber(String.format("%02d/QĐ-ĐHL", doc.getId()));
        }

        // Kiểm tra chống trùng số văn bản đã có trên hệ thống
        if (doc.getOfficialNumber() != null && documentRepository.existsByOfficialNumberAndIdNotAndDeleteFlgFalse(doc.getOfficialNumber().trim(), doc.getId())) {
            throw new BusinessException(ErrorCode.DOCUMENT_NUMBER_ALREADY_EXISTS,
                    "Số hiệu văn bản '" + doc.getOfficialNumber().trim() + "' đã tồn tại trên một văn bản khác trong hệ thống!");
        }
        doc.setStatus(DocumentStatus.PUBLISHED);
        doc.setOfficialHash(computedHash);

        if (request != null && request.getIssueDate() != null) {
            doc.setIssueDate(request.getIssueDate());
        } else {
            doc.setIssueDate(LocalDate.now(Document.getBusinessZone()));
        }

        if (request != null && request.getEffectiveDate() != null) {
            doc.setEffectiveDate(request.getEffectiveDate());
        }
    }

    private void distributeDocument(Long documentId, PublishRequest request) {
        if (request == null) {
            return;
        }

        if (Boolean.TRUE.equals(request.getIsPublicToAll())) {
            distributionRepository.save(DocumentDistribution.builder()
                    .documentId(documentId)
                    .targetType("ALL")
                    .isRead(false)
                    .build());
            return;
        }

        List<Long> targets = extractTargetIds(request);
        for (Long orgId : targets) {
            distributionRepository.save(DocumentDistribution.builder()
                    .documentId(documentId)
                    .targetId(orgId)
                    .targetType("ORG")
                    .isRead(false)
                    .build());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> extractTargetIds(PublishRequest request) {
        try {
            var method = request.getClass().getMethod("getTargetIds");
            Object result = method.invoke(request);
            if (result instanceof List<?>) {
                return (List<Long>) result;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fallback sang getOrgIds nếu DTO sử dụng tên cũ
        }

        try {
            var method = request.getClass().getMethod("getOrgIds");
            Object result = method.invoke(request);
            if (result instanceof List<?>) {
                return (List<Long>) result;
            }
        } catch (ReflectiveOperationException ignored) {
            // Trường hợp DTO không khai báo trường targets
        }

        return Collections.emptyList();
    }

    /**
     * Backward-compatible overload for internal callers and legacy invocations.
     * Explicitly resolves the current authentication from SecurityContextHolder
     * and delegates to {@link #verifyIntegrity(Long, Authentication)}.
     *
     * @param documentId the ID of the document to verify
     * @return true if document matches official SHA-256 hash, false otherwise
     */
    public boolean verifyIntegrity(Long documentId) {
        return verifyIntegrity(documentId, null);
    }

    public boolean verifyIntegrity(Long documentId, org.springframework.security.core.Authentication authentication) {
        if (authentication == null) {
            authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        }
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        assertCanVerifyIntegrity(doc, authentication);

        if (doc.getOfficialHash() == null || doc.getOfficialHash().isBlank()) {
            return false;
        }

        String currentHash = calculateSha256(doc.getOfficialFilePath());
        return MessageDigest.isEqual(
                doc.getOfficialHash().getBytes(StandardCharsets.UTF_8),
                currentHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    void assertCanVerifyIntegrity(Document doc, org.springframework.security.core.Authentication authentication) {
        // Enforce user authentication first before inspecting document status
        if (authentication == null 
                || !authentication.isAuthenticated() 
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            throw new org.springframework.security.access.AccessDeniedException("Yêu cầu xác thực người dùng");
        }

        if (doc.getStatus() == DocumentStatus.PUBLISHED) {
            return;
        }

        boolean hasAdminOrPrivilege = authentication.getAuthorities() != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                            || a.getAuthority().equals("ADMIN")
                            || a.getAuthority().equals("ROLE_SYSTEM_ADMIN")
                            || a.getAuthority().equals("SYSTEM_ADMIN")
                            || a.getAuthority().equals("DOCUMENT_VERIFY")
                            || a.getAuthority().equals("DOCUMENT_PUBLISH"));
        if (hasAdminOrPrivilege) {
            return;
        }

        Long userId = extractUserId(authentication);
        Long creatorId = doc.getDrafter() != null ? doc.getDrafter().getId() : null;
        if (userId != null && userId.equals(creatorId)) {
            return;
        }

        throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền kiểm tra tính toàn vẹn của tài liệu này");
    }

    Long extractUserId(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof vn.edu.epu.eoffice_be.security.UserIdAwarePrincipal userAware) {
            return userAware.getUserId();
        }
        return null;
    }

    private String calculateSha256(String filePath) {
        // Storage failures must reach the caller; neither a path nor a placeholder is PDF content.
        byte[] pdf = documentPdfStorageService.readPdf(filePath);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Lỗi khi tạo mã băm SHA-256", e);
        }
    }
}
