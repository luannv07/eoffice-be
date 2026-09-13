package vn.edu.epu.eoffice_be.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DocumentWorkflowLogTest {

    @Test
    @DisplayName("builder().createdAt(LocalDateTime): Gán chính xác createdAt và createdDate kế thừa từ BaseEntity")
    void builder_withCreatedAt_shouldPopulateCreatedAtAndCreatedDate() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 14, 30, 0);

        DocumentWorkflowLog log = DocumentWorkflowLog.builder()
                .id(101L)
                .action(WorkflowAction.SUBMIT)
                .createdAt(now)
                .build();

        assertNotNull(log.getCreatedAt(), "createdAt không được null");
        assertEquals(now, log.getCreatedAt());

        assertNotNull(log.getCreatedDate(), "createdDate kế thừa từ BaseEntity phải được điền");
        Instant expectedInstant = now.atZone(Document.getBusinessZone()).toInstant();
        assertEquals(expectedInstant, log.getCreatedDate());
    }

    @Test
    @DisplayName("builder().createdDate(Instant): Gán chính xác createdDate và quy đổi createdAt tương ứng")
    void builder_withCreatedDate_shouldPopulateCreatedDateAndCreatedAt() {
        Instant instant = Instant.parse("2026-09-13T07:30:00Z");

        DocumentWorkflowLog log = DocumentWorkflowLog.builder()
                .id(102L)
                .action(WorkflowAction.LEADER_APPROVE)
                .createdDate(instant)
                .build();

        assertEquals(instant, log.getCreatedDate());
        assertNotNull(log.getCreatedAt());
        assertEquals(LocalDateTime.ofInstant(instant, Document.getBusinessZone()), log.getCreatedAt());
    }

    @Test
    @DisplayName("builder().createdAt(null) & createdDate(null): Xử lý null an toàn")
    void builder_withNullCreatedAt_shouldSetNull() {
        DocumentWorkflowLog log1 = DocumentWorkflowLog.builder()
                .createdAt(null)
                .build();
        assertNull(log1.getCreatedAt());
        assertNull(log1.getCreatedDate());

        DocumentWorkflowLog log2 = DocumentWorkflowLog.builder()
                .createdDate(null)
                .build();
        assertNull(log2.getCreatedAt());
        assertNull(log2.getCreatedDate());
    }

    @Test
    @DisplayName("builder() không truyền createdAt/createdDate: Để trống cho JPA Auditing tự động điền")
    void builder_withoutCreatedAt_shouldLeaveNullForJpaAuditing() {
        DocumentWorkflowLog log = DocumentWorkflowLog.builder()
                .id(1L)
                .build();

        assertNull(log.getCreatedAt());
        assertNull(log.getCreatedDate());
    }

    @Test
    @DisplayName("getCreatedAt & setCreatedAt: Hoạt động chính xác trên instance thực thể")
    void setCreatedAt_and_getCreatedAt_shouldWorkCorrectly() {
        DocumentWorkflowLog log = new DocumentWorkflowLog();
        LocalDateTime ldt = LocalDateTime.of(2026, 9, 13, 8, 0, 0);

        log.setCreatedAt(ldt);

        assertEquals(ldt, log.getCreatedAt());
        assertEquals(ldt.atZone(Document.getBusinessZone()).toInstant(), log.getCreatedDate());

        log.setCreatedAt(null);
        assertNull(log.getCreatedAt());
        assertNull(log.getCreatedDate());
    }

    @Test
    @DisplayName("DocumentWorkflowLogBuilder helper methods: documentId, action(String), action(WorkflowAction)")
    void builder_helpers_shouldWorkCorrectly() {
        DocumentWorkflowLog log = DocumentWorkflowLog.builder()
                .documentId(55L)
                .action("REVIEW_APPROVE")
                .fromStatus(DocumentStatus.SUBMITTED)
                .toStatus(DocumentStatus.IN_REVIEW)
                .comment("Da xem xet")
                .leadershipInstruction("Chuyen phong ban chu tri")
                .build();

        assertNotNull(log.getDocument());
        assertEquals(55L, log.getDocument().getId());
        assertEquals(55L, log.getDocumentId());
        assertEquals(WorkflowAction.REVIEW_APPROVE, log.getAction());
        assertEquals(DocumentStatus.SUBMITTED, log.getFromStatus());
        assertEquals(DocumentStatus.IN_REVIEW, log.getToStatus());
        assertEquals("Da xem xet", log.getComment());
        assertEquals("Chuyen phong ban chu tri", log.getLeadershipInstruction());
        assertFalse(log.isDeleteFlg());

        // Test fallback khi action string không hợp lệ
        DocumentWorkflowLog logFallback = DocumentWorkflowLog.builder()
                .action("INVALID_ACTION_STRING")
                .build();
        assertEquals(WorkflowAction.SUBMIT, logFallback.getAction());
    }
}
