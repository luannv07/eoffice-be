package vn.edu.epu.eoffice_be.service;

import vn.edu.epu.eoffice_be.dto.PendingDocumentResponse;
import vn.edu.epu.eoffice_be.dto.WorkflowTimelineResponse;

import java.util.List;

public interface DocumentWorkflowService {

    List<WorkflowTimelineResponse> getDocumentTimeline(Long documentId);

    List<PendingDocumentResponse> getPendingDocuments();

    void logAction(Long documentId, String username, String role, String action, String comment);
}