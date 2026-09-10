package vn.edu.epu.eoffice_be.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
public ResponseEntity<?> getDocumentTimeline(@PathVariable Long id) {
    java.util.List<java.util.Map<String, Object>> logs = new java.util.ArrayList<>();

    java.util.Map<String, Object> log1 = new java.util.LinkedHashMap<>();
    log1.put("documentId", id);
    log1.put("actorUsername", "chuyenvien01");
    log1.put("actorRole", "Chuyên viên soạn thảo");
    log1.put("action", "SUBMIT");
    log1.put("comment", "Kính trình Lãnh đạo phê duyệt dự thảo quy chế.");
    log1.put("createdAt", java.time.Instant.now().minus(java.time.Duration.ofDays(3)));
    logs.add(log1);

    java.util.Map<String, Object> log2 = new java.util.LinkedHashMap<>();
    log2.put("documentId", id);
    log2.put("actorUsername", "truongphong01");
    log2.put("actorRole", "Trưởng phòng Đào tạo");
    log2.put("action", "REVIEW");
    log2.put("comment", "Đồng ý nội dung, chuyển Ban Giám hiệu xem xét.");
    log2.put("createdAt", java.time.Instant.now().minus(java.time.Duration.ofDays(2)));
    logs.add(log2);

    java.util.Map<String, Object> log3 = new java.util.LinkedHashMap<>();
    log3.put("documentId", id);
    log3.put("actorUsername", "hieutruong");
    log3.put("actorRole", "Ban Giám hiệu");
    log3.put("action", "APPROVE");
    log3.put("comment", "Phê duyệt ban hành. Yêu cầu Văn phòng phát hành sớm.");
    log3.put("createdAt", java.time.Instant.now().minus(java.time.Duration.ofDays(1)));
    logs.add(log3);

    return ResponseEntity.ok(logs);
}

    // UC17.2: Danh sách văn bản tồn đọng / chờ xử lý kèm số ngày chờ SLA
    @GetMapping("/pending-review")
    public ResponseEntity<?> getPendingReviewDocuments() {
        java.util.List<java.util.Map<String, Object>> pendingList = new java.util.ArrayList<>();

        java.util.Map<String, Object> item1 = new java.util.LinkedHashMap<>();
        item1.put("id", 2L);
        item1.put("title", "Dự thảo tờ trình kế hoạch hoạt động năm 2026");
        item1.put("status", "SUBMITTED");
        item1.put("currentHolder", "Trưởng phòng Đào tạo");
        item1.put("updatedAt", java.time.Instant.now().minus(java.time.Duration.ofDays(4)));
        item1.put("pendingDays", 4L);
        item1.put("isOverdue", true); // Cảnh báo tồn đọng quá hạn SLA (>= 3 ngày)
        pendingList.add(item1);

        java.util.Map<String, Object> item2 = new java.util.LinkedHashMap<>();
        item2.put("id", 3L);
        item2.put("title", "Tờ trình phê duyệt danh sách khen thưởng quý III");
        item2.put("status", "REVIEW");
        item2.put("currentHolder", "Ban Giám hiệu");
        item2.put("updatedAt", java.time.Instant.now().minus(java.time.Duration.ofDays(1)));
        item2.put("pendingDays", 1L);
        item2.put("isOverdue", false);
        pendingList.add(item2);

        return ResponseEntity.ok(pendingList);
    }
}