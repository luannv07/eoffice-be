package vn.edu.epu.eoffice_be.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class DocumentWorkflowLogTest {

    @Test
    @DisplayName("Lombok Builder: Khởi tạo đầy đủ các trường và giá trị mặc định của deleteFlg")
    void builder_withAllFields_shouldCreateEntityCorrectly() {
        Document doc = Document.builder().id(55L).build();
        UserLogin actor = new UserLogin();
        OrgNode org = new OrgNode();

        DocumentWorkflowLog log = DocumentWorkflowLog.builder()
                .id(101L)
                .document(doc)
                .action(WorkflowAction.SUBMIT)
                .actor(actor)
                .fromStatus(DocumentStatus.DRAFT)
                .toStatus(DocumentStatus.SUBMITTED)
                .comment("Da nop ho so")
                .leadershipInstruction("Chuyen phong ban chu tri")
                .assignedOrgNode(org)
                .signatureHash("abc123hash")
                .deleteFlg(false)
                .build();

        assertEquals(101L, log.getId());
        assertEquals(doc, log.getDocument());
        assertEquals(55L, log.getDocumentId());
        assertEquals(WorkflowAction.SUBMIT, log.getAction());
        assertEquals(actor, log.getActor());
        assertEquals(DocumentStatus.DRAFT, log.getFromStatus());
        assertEquals(DocumentStatus.SUBMITTED, log.getToStatus());
        assertEquals("Da nop ho so", log.getComment());
        assertEquals("Chuyen phong ban chu tri", log.getLeadershipInstruction());
        assertEquals(org, log.getAssignedOrgNode());
        assertEquals("abc123hash", log.getSignatureHash());
        assertFalse(log.isDeleteFlg());
    }

    @Test
    @DisplayName("Lombok Builder: Giá trị mặc định deleteFlg = false khi không truyền")
    void builder_defaultDeleteFlg_shouldBeFalse() {
        DocumentWorkflowLog log = DocumentWorkflowLog.builder()
                .id(1L)
                .build();

        assertFalse(log.isDeleteFlg());
        assertNull(log.getDocument());
        assertNull(log.getDocumentId());
        assertNull(log.getCreatedAt());
        assertNull(log.getCreatedDate());
    }

    @Test
    @DisplayName("getDocumentId: Trả về document.getId() hoặc null an toàn")
    void getDocumentId_shouldReturnCorrectIdOrNull() {
        Document doc = Document.builder().id(99L).build();
        DocumentWorkflowLog logWithDoc = DocumentWorkflowLog.builder().document(doc).build();
        assertEquals(99L, logWithDoc.getDocumentId());

        DocumentWorkflowLog logWithoutDoc = DocumentWorkflowLog.builder().build();
        assertNull(logWithoutDoc.getDocumentId());
    }

    @Test
    @DisplayName("getCreatedAt: Quy đổi chính xác createdDate kế thừa từ BaseEntity sang múi giờ Việt Nam")
    void getCreatedAt_shouldConvertCreatedDateToVietnamTime() {
        DocumentWorkflowLog log = new DocumentWorkflowLog();
        Instant instant = Instant.parse("2026-09-13T07:30:00Z");

        log.setCreatedDate(instant);

        assertNotNull(log.getCreatedAt());
        assertEquals(LocalDateTime.ofInstant(instant, Document.getBusinessZone()), log.getCreatedAt());

        log.setCreatedDate(null);
        assertNull(log.getCreatedAt());
    }

    @Test
    @DisplayName("Audit entity immutability: Sensitive audit fields must not have public setter methods")
    void sensitiveFields_shouldNotHavePublicSetters() {
        String[] sensitiveSetters = {
                "setId", "setDocument", "setActor", "setAction",
                "setFromStatus", "setToStatus", "setSignatureHash",
                "setComment", "setLeadershipInstruction", "setAssignedOrgNode"
        };
        for (String setterName : sensitiveSetters) {
            boolean hasSetter = Arrays.stream(DocumentWorkflowLog.class.getMethods())
                    .anyMatch(m -> m.getName().equals(setterName));
            assertFalse(hasSetter, "Sensitive audit field setter " + setterName + " must not exist!");
        }
    }

    @Test
    @DisplayName("setDeleteFlg: Controlled update for soft delete works properly")
    void deleteFlg_controlledMutation_shouldWork() {
        DocumentWorkflowLog log = DocumentWorkflowLog.builder().build();
        assertFalse(log.isDeleteFlg());

        log.setDeleteFlg(true);
        assertTrue(log.isDeleteFlg());

        log.setDeleteFlg(false);
        assertFalse(log.isDeleteFlg());
    }

    @Test
    @DisplayName("Constructor: Khi truyền cả createdDate và createdAt thì ưu tiên createdDate và không ném ngoại lệ")
    void constructor_withBothCreatedDateAndCreatedAt_shouldPrioritizeCreatedDateWithoutThrowing() {
        Instant instant = Instant.parse("2026-09-13T07:30:00Z");
        LocalDateTime localDateTime = LocalDateTime.of(2026, 9, 13, 15, 0, 0);

        DocumentWorkflowLog log = assertDoesNotThrow(() -> new DocumentWorkflowLog(
                1L, null, WorkflowAction.SUBMIT, null,
                DocumentStatus.DRAFT, DocumentStatus.SUBMITTED, "ok",
                null, null, null, false, instant, localDateTime
        ));

        assertEquals(instant, log.getCreatedDate());
    }
}