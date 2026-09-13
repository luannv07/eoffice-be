package vn.edu.epu.eoffice_be.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentWorkflowLog;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.UserLogin;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DocumentWorkflowLogRepositoryTest {

    @Autowired
    private DocumentWorkflowLogRepository workflowLogRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserLoginRepository userLoginRepository;

    @Autowired
    private OrgNodeRepository orgNodeRepository;

    private UserLogin getOrCreateUser() {
        return userLoginRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("UserLogin not found"));
    }

    private OrgNode getOrCreateOrg() {
        return orgNodeRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("OrgNode not found"));
    }

    private Document createTestDocument(String title) {
        UserLogin user = getOrCreateUser();
        OrgNode org = getOrCreateOrg();
        Document doc = Document.builder()
                .title(title)
                .documentTypeCd("CONG_VAN")
                .drafter(user)
                .issuingOrg(org)
                .status(DocumentStatus.DRAFT)
                .deleteFlg(false)
                .build();
        return documentRepository.save(doc);
    }

    @Test
    @DisplayName("findByDocumentIdAndDeleteFlgFalse: Phân trang xác định với ORDER BY l.createdDate ASC, l.id ASC và loại trừ xóa mềm")
    void findByDocumentIdAndDeleteFlgFalse_shouldOrderDeterministicallyAndExcludeDeleted() throws Exception {
        Document targetDoc = createTestDocument("Target Document For Log Testing");
        Document otherDoc = createTestDocument("Other Document For Log Testing");
        UserLogin actor = getOrCreateUser();

        // Chuẩn bị các mốc thời gian tăng dần
        Instant t1 = Instant.parse("2026-09-13T01:00:00Z");
        Instant t2 = Instant.parse("2026-09-13T02:00:00Z");
        Instant t3 = Instant.parse("2026-09-13T03:00:00Z");
        Instant tDeleted = Instant.parse("2026-09-13T01:30:00Z");

        DocumentWorkflowLog log1 = DocumentWorkflowLog.builder()
                .document(targetDoc)
                .actor(actor)
                .action(WorkflowAction.SUBMIT)
                .fromStatus(DocumentStatus.DRAFT)
                .toStatus(DocumentStatus.SUBMITTED)
                .comment("Gửi duyệt T1")
                .createdDate(t1)
                .deleteFlg(false)
                .build();

        DocumentWorkflowLog log2 = DocumentWorkflowLog.builder()
                .document(targetDoc)
                .actor(actor)
                .action(WorkflowAction.REVIEW_APPROVE)
                .fromStatus(DocumentStatus.SUBMITTED)
                .toStatus(DocumentStatus.IN_REVIEW)
                .comment("Thẩm định T2")
                .createdDate(t2)
                .deleteFlg(false)
                .build();

        DocumentWorkflowLog log3 = DocumentWorkflowLog.builder()
                .document(targetDoc)
                .actor(actor)
                .action(WorkflowAction.LEADER_APPROVE)
                .fromStatus(DocumentStatus.IN_REVIEW)
                .toStatus(DocumentStatus.APPROVED)
                .comment("Lãnh đạo duyệt T3")
                .createdDate(t3)
                .deleteFlg(false)
                .build();

        DocumentWorkflowLog logDeleted = DocumentWorkflowLog.builder()
                .document(targetDoc)
                .actor(actor)
                .action(WorkflowAction.REQUEST_REVISION)
                .fromStatus(DocumentStatus.IN_REVIEW)
                .toStatus(DocumentStatus.NEEDS_REVISION)
                .comment("Log xóa mềm")
                .createdDate(tDeleted)
                .deleteFlg(true)
                .build();

        DocumentWorkflowLog logOther = DocumentWorkflowLog.builder()
                .document(otherDoc)
                .actor(actor)
                .action(WorkflowAction.SUBMIT)
                .fromStatus(DocumentStatus.DRAFT)
                .toStatus(DocumentStatus.SUBMITTED)
                .comment("Log của văn bản khác")
                .createdDate(t1)
                .deleteFlg(false)
                .build();

        // Chèn vào DB theo trình tự thời gian thực tế với khoảng cách thời gian nhỏ để JPA Auditing ghi nhận
        workflowLogRepository.save(log1);
        Thread.sleep(20);
        workflowLogRepository.save(logDeleted);
        Thread.sleep(20);
        workflowLogRepository.save(log2);
        Thread.sleep(20);
        workflowLogRepository.save(logOther);
        Thread.sleep(20);
        workflowLogRepository.save(log3);

        // 1. Kiểm tra Trang 0 (kích thước 2, không truyền sort từ caller)
        Page<DocumentWorkflowLog> page0 = workflowLogRepository.findByDocumentIdAndDeleteFlgFalse(
                targetDoc.getId(),
                PageRequest.of(0, 2)
        );

        assertNotNull(page0);
        assertEquals(3, page0.getTotalElements(), "Tổng số bản ghi hoạt động phải là 3 (đã loại logDeleted và logOther)");
        assertEquals(2, page0.getTotalPages());
        assertEquals(2, page0.getContent().size());

        // Xác minh thứ tự trang 0: Phải là log1 (T1) rồi đến log2 (T2)
        assertEquals(log1.getId(), page0.getContent().get(0).getId());
        assertEquals("Gửi duyệt T1", page0.getContent().get(0).getComment());
        assertEquals(log2.getId(), page0.getContent().get(1).getId());
        assertEquals("Thẩm định T2", page0.getContent().get(1).getComment());

        // 2. Kiểm tra Trang 1: Phải là log3 (T3)
        Page<DocumentWorkflowLog> page1 = workflowLogRepository.findByDocumentIdAndDeleteFlgFalse(
                targetDoc.getId(),
                PageRequest.of(1, 2)
        );

        assertNotNull(page1);
        assertEquals(1, page1.getContent().size());
        assertEquals(log3.getId(), page1.getContent().get(0).getId());
        assertEquals("Lãnh đạo duyệt T3", page1.getContent().get(0).getComment());
    }
}
