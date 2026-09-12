package vn.edu.epu.eoffice_be.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.dto.PublishRequest;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentDistribution;
import vn.edu.epu.eoffice_be.repository.DocumentDistributionRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentPublishService {

    DocumentRepository documentRepository;
    DocumentDistributionRepository distributionRepository;

    @Transactional
    public Document publishDocument(Long documentId, PublishRequest request) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        String computedHash = calculateSha256(doc.getOfficialFilePath());
        applyDocumentMetadata(doc, request, computedHash);

        Document savedDoc = documentRepository.save(doc);
        distributeDocument(savedDoc.getId(), request);

        return savedDoc;
    }

    private void applyDocumentMetadata(Document doc, PublishRequest request, String computedHash) {
        doc.setOfficialNumber(String.format("%02d/QĐ-ĐHL", doc.getId()));
        doc.setStatus(DocumentStatus.PUBLISHED);
        doc.setOfficialHash(computedHash);

        if (request != null && request.getIssueDate() != null) {
            doc.setIssueDate(request.getIssueDate());
        } else {
            doc.setIssueDate(LocalDate.now(ZoneId.systemDefault()));
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

        try {
            String currentHash = calculateSha256(doc.getOfficialFilePath());
            return MessageDigest.isEqual(
                    doc.getOfficialHash().getBytes(StandardCharsets.UTF_8),
                    currentHash.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    void assertCanVerifyIntegrity(Document doc, org.springframework.security.core.Authentication authentication) {
        if (doc.getStatus() == DocumentStatus.PUBLISHED || doc.getStatus() == DocumentStatus.ISSUED) {
            return;
        }

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException("Yêu cầu xác thực người dùng");
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
        Long creatorId = doc.getCreatorUser() != null ? doc.getCreatorUser().getId() : null;
        if (userId != null && userId.equals(creatorId)) {
            return;
        }

        throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền kiểm tra tính toàn vẹn của tài liệu này");
    }

    private Long extractUserId(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof vn.edu.epu.eoffice_be.security.UserIdAwarePrincipal uap) {
            return uap.getUserId();
        }
        if (principal instanceof vn.edu.epu.eoffice_be.security.JwtUserPrincipal jwt) {
            return jwt.getUserId();
        }
        return null;
    }

    private String calculateSha256(String filePath) {
        if (filePath != null && !filePath.isBlank()) {
            File file = new File(filePath);
            if (file.exists() && file.isFile()) {
                try {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    try (InputStream is = new FileInputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            digest.update(buffer, 0, read);
                        }
                        return bytesToHex(digest.digest());
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("Lỗi khi tính toán mã băm SHA-256 cho tệp", e);
                }
            }
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String fallbackData = (filePath != null) ? filePath : "DEFAULT_DOCUMENT_PAYLOAD";
            byte[] hash = digest.digest(fallbackData.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Lỗi khi tạo mã băm SHA-256", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}