package vn.edu.epu.eoffice_be.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import vn.edu.epu.eoffice_be.dto.PendingDocumentResponse;
import vn.edu.epu.eoffice_be.dto.WorkflowTimelineResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentWorkflowLog;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentWorkflowLogRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentWorkflowService {
    private static final java.time.ZoneId ZONE_VN = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
    DocumentWorkflowLogRepository workflowLogRepository;
    DocumentRepository documentRepository;

    public List<WorkflowTimelineResponse> getDocumentTimeline(Long documentId, org.springframework.security.core.Authentication authentication) {
        verifyUserCanAccessDocument(documentId, authentication);
        return getDocumentTimeline(documentId);
    }

    public List<WorkflowTimelineResponse> getDocumentTimeline(Long documentId) {
        return workflowLogRepository.findByDocumentIdOrderByCreatedAtAsc(documentId).stream()
                .<WorkflowTimelineResponse>map(log -> WorkflowTimelineResponse.builder()
                        .id(log.getId())
                        .documentId(log.getDocumentId())
                        .actorUsername(log.getActorUsername() != null ? log.getActorUsername() : log.getActor())
                        .actorRole(log.getActorRole())
                        .action(log.getAction())
                        .comment(log.getComment())
                        .createdAt(log.getCreatedAt())
                        .build())
                .toList();
    }

    private static final List<vn.edu.epu.eoffice_be.common.enums.DocumentStatus> PENDING_STATUSES = List.of(
            vn.edu.epu.eoffice_be.common.enums.DocumentStatus.WAITING_APPROVAL
    );

    public org.springframework.data.domain.Page<PendingDocumentResponse> getPendingDocuments(org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<Document> page = documentRepository.findByStatusIn(PENDING_STATUSES, pageable);
        if (page == null) {
            return org.springframework.data.domain.Page.empty(pageable);
        }

        return page.map(this::mapToPendingResponse);
    }

    public List<PendingDocumentResponse> getPendingDocuments() {
        List<Document> list = documentRepository.findByStatusIn(PENDING_STATUSES);
        if (list == null) {
            return java.util.Collections.emptyList();
        }
        return list.stream().map(this::mapToPendingResponse).toList();
    }

    private PendingDocumentResponse mapToPendingResponse(Document doc) {
        String statusName = doc.getStatus() != null ? doc.getStatus().name() : "";
        String holder = switch (statusName) {
            case "SUBMITTED" -> "Văn thư tiếp nhận";
            case "PENDING_REVIEW" -> "Chuyên viên thẩm định";
            case "PENDING_APPROVAL", "WAITING_APPROVAL" -> "Lãnh đạo phê duyệt";
            default -> "Bộ phận xử lý văn bản";
        };

        return PendingDocumentResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .status(statusName)
                .currentHolder(holder)
                .build();
    }

    public void verifyUserCanAccessDocument(Long documentId, org.springframework.security.core.Authentication authentication) {
        if (authentication == null 
                || !authentication.isAuthenticated() 
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            throw new org.springframework.security.access.AccessDeniedException("Yêu cầu xác thực người dùng");
        }

        boolean isAdmin = authentication.getAuthorities() != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                            || a.getAuthority().equals("ADMIN")
                            || a.getAuthority().equals("ROLE_SYSTEM_ADMIN")
                            || a.getAuthority().equals("SYSTEM_ADMIN"));
        if (isAdmin) {
            return;
        }

        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new vn.edu.epu.eoffice_be.common.exception.BusinessException(
                        vn.edu.epu.eoffice_be.common.enums.ErrorCode.DOCUMENT_NOT_FOUND,
                        "Không tìm thấy văn bản: " + documentId));

        if (doc.getStatus() == vn.edu.epu.eoffice_be.common.enums.DocumentStatus.PUBLISHED
                || doc.getStatus() == vn.edu.epu.eoffice_be.common.enums.DocumentStatus.ISSUED) {
            return;
        }

        Long userId = extractUserId(authentication);
        Long creatorId = doc.getCreatorUser() != null ? doc.getCreatorUser().getId() : null;
        if (userId != null && userId.equals(creatorId)) {
            return;
        }

        throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền xem timeline của tài liệu này");
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

    public void logAction(Long documentId, String username, String role, String action, String comment) {
        DocumentWorkflowLog log = DocumentWorkflowLog.builder()
                .documentId(documentId)
                .actorUsername(username)
                .actorRole(role)
                .action(action)
                .comment(comment)
                .createdAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")))
                .build();
        workflowLogRepository.save(log);
    }
}