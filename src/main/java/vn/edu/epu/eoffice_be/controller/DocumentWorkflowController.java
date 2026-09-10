package vn.edu.epu.eoffice_be.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.epu.eoffice_be.dto.PendingDocumentResponse;
import vn.edu.epu.eoffice_be.dto.WorkflowTimelineResponse;
import vn.edu.epu.eoffice_be.service.DocumentWorkflowService;

import java.util.List;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentWorkflowController {

    DocumentWorkflowService workflowService;

    // UC17.1: Xem timeline luồng điều hành & bút phê lãnh đạo
    @GetMapping("/{id}/timeline")
    public ResponseEntity<List<WorkflowTimelineResponse>> getDocumentTimeline(@PathVariable("id") Long id) {
        List<WorkflowTimelineResponse> timeline = workflowService.getDocumentTimeline(id);
        return ResponseEntity.status(HttpStatus.OK).body(timeline);
    }

    // UC17.2: Danh sách văn bản tồn đọng / chờ xử lý kèm số ngày chờ SLA
    @GetMapping("/pending-review")
    public ResponseEntity<List<PendingDocumentResponse>> getPendingReviewDocuments() {
        List<PendingDocumentResponse> pendingList = workflowService.getPendingDocuments();
        return ResponseEntity.status(HttpStatus.OK).body(pendingList);
    }
}