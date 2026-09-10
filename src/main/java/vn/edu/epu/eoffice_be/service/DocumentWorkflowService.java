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

    DocumentWorkflowLogRepository workflowLogRepository;
    DocumentRepository documentRepository;

    public List<WorkflowTimelineResponse> getDocumentTimeline(Long documentId) {
        return workflowLogRepository.findByDocumentIdOrderByCreatedAtAsc(documentId).stream()
                .map(log -> WorkflowTimelineResponse.builder()
                        .id(log.getId())
                        .documentId(log.getDocumentId())
                        .actorUsername(log.getActorUsername())
                        .actorRole(log.getActorRole())
                        .action(log.getAction())
                        .comment(log.getComment())
                        .createdAt(log.getCreatedAt())
                        .build())
                .toList();
    }

    public List<PendingDocumentResponse> getPendingDocuments() {
        Instant now = Instant.now();
        List<Document> list = documentRepository.findAll();

        return list.stream()
                .filter(doc -> !"PUBLISHED".equalsIgnoreCase(doc.getStatus()) && !"REJECTED".equalsIgnoreCase(doc.getStatus()))
                .map(doc -> {
                    Instant lastUpdate = (doc.getUpdatedDate() != null) ? doc.getUpdatedDate() : now;
                    long pendingDays = Duration.between(lastUpdate, now).toDays();
                    return PendingDocumentResponse.builder()
                            .id(doc.getId())
                            .title(doc.getTitle())
                            .status(doc.getStatus())
                            .currentHolder("Lãnh đạo phê duyệt")
                            .updatedAt(lastUpdate)
                            .pendingDays(pendingDays)
                            .isOverdue(pendingDays >= 3)
                            .build();
                })
                .toList();
    }

    public void logAction(Long documentId, String username, String role, String action, String comment) {
        DocumentWorkflowLog log = DocumentWorkflowLog.builder()
                .documentId(documentId)
                .actorUsername(username)
                .actorRole(role)
                .action(action)
                .comment(comment)
                .createdAt(LocalDateTime.now())
                .build();
        workflowLogRepository.save(log);
    }
}