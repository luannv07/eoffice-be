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

    public List<PendingDocumentResponse> getPendingDocuments() {
        // [17/20]: Lọc trực tiếp theo danh sách trạng thái chờ xử lý thay vì findAll()
        List<String> pendingStatuses = List.of("SUBMITTED", "PENDING_REVIEW", "PENDING_APPROVAL");
        List<Document> list = documentRepository.findByStatusIn(pendingStatuses);

        return list.stream().map(doc -> {
            // [16/20]: Xác định currentHolder dựa trên trạng thái thực tế
            String holder = switch (doc.getStatus()) {
                case "SUBMITTED" -> "Văn thư tiếp nhận";
                case "PENDING_REVIEW" -> "Chuyên viên thẩm định";
                case "PENDING_APPROVAL" -> "Lãnh đạo phê duyệt";
                default -> "Bộ phận xử lý văn bản";
            };

            return PendingDocumentResponse.builder()
                    .id(doc.getId())
                    .title(doc.getTitle())
                    .status(doc.getStatus())
                    .currentHolder(holder)
                    .build();
        }).toList();
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