package vn.edu.epu.eoffice_be.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;
import vn.edu.epu.eoffice_be.dto.PendingDocumentResponse;
import vn.edu.epu.eoffice_be.dto.WorkflowTimelineResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentWorkflowLog;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentWorkflowLogRepository;
import vn.edu.epu.eoffice_be.service.DocumentWorkflowService;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentWorkflowServiceImpl implements DocumentWorkflowService {

    DocumentWorkflowLogRepository workflowLogRepository;
    DocumentRepository documentRepository;

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowTimelineResponse> getDocumentTimeline(Long documentId) {
        List<DocumentWorkflowLog> logs = workflowLogRepository.findByDocumentIdOrderByCreatedDateAsc(documentId);
        if (logs == null || logs.isEmpty()) {
            return Collections.emptyList();
        }

        return logs.stream().map(log -> {
            String username = (log.getActor() != null && log.getActor().getUsername() != null)
                    ? log.getActor().getUsername()
                    : "SYSTEM";

            return WorkflowTimelineResponse.builder()
                    .id(log.getId())
                    .documentId(log.getDocumentId())
                    .actorUsername(username)
                    .actorRole(null)
                    .action(log.getAction() != null ? log.getAction().name() : null)
                    .comment(log.getComment())
                    .createdAt(log.getCreatedAt())
                    .build();
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingDocumentResponse> getPendingDocuments() {
        List<String> pendingStatuses = List.of("SUBMITTED", "PENDING_REVIEW", "PENDING_APPROVAL");
        List<Document> list = documentRepository.findByStatusIn(pendingStatuses);

        return list.stream().map(doc -> {
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

    @Override
    @Transactional
    public void logAction(Long documentId, String username, String role, String action, String comment) {
        WorkflowAction workflowAction;
        try {
            workflowAction = action != null ? WorkflowAction.valueOf(action.trim().toUpperCase()) : WorkflowAction.OTHER;
        } catch (Exception e) {
            workflowAction = WorkflowAction.OTHER;
        }

        UserLogin actor = new UserLogin();
        actor.setUsername(username);

        DocumentWorkflowLog log = DocumentWorkflowLog.builder()
                .document(Document.builder().id(documentId).build())
                .actor(actor)
                .action(workflowAction)
                .comment(comment)
                .build();

        workflowLogRepository.save(log);
    }
}