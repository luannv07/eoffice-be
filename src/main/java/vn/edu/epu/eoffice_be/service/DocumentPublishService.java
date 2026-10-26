package vn.edu.epu.eoffice_be.service;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentPublishService {

    final DocumentRepository documentRepository;
    final DocumentPdfStorageService documentPdfStorageService;

    @Autowired
    public DocumentPublishService(DocumentRepository documentRepository,
                                  DocumentPdfStorageService documentPdfStorageService) {
        this.documentRepository = documentRepository;
        this.documentPdfStorageService = Objects.requireNonNull(
                documentPdfStorageService, "documentPdfStorageService must not be null");
    }

    /**
     * Checks integrity using the current authenticated user.
     * Explicitly resolves the current authentication from SecurityContextHolder
     * and delegates to {@link #verifyIntegrity(Long, org.springframework.security.core.Authentication)}.
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
                            || a.getAuthority().equals("DOCUMENT_VERIFY"));
        if (hasAdminOrPrivilege) {
            return;
        }

        Long userId = extractUserId(authentication);
        Long creatorId = doc.getCreatorUser() != null ? doc.getCreatorUser().getId() : null;
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
