package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.dto.PendingDocumentResponse;
import vn.edu.epu.eoffice_be.dto.WorkflowTimelineResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.OrgNodeRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.service.impl.DocumentWorkflowServiceImpl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DocumentWorkflowServiceIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DocumentWorkflowService documentWorkflowService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserLoginRepository userLoginRepository;

    @Autowired
    private OrgNodeRepository orgNodeRepository;

    @Test
    @DisplayName("Context loads: DocumentWorkflowService bean is successfully injected and is instance of DocumentWorkflowServiceImpl")
    void contextLoads_andDocumentWorkflowServiceIsInjected() {
        assertNotNull(documentWorkflowService, "DocumentWorkflowService bean must be injected by Spring");
        assertInstanceOf(DocumentWorkflowServiceImpl.class, documentWorkflowService,
                "Spring bean must be an instance of DocumentWorkflowServiceImpl");
        assertTrue(applicationContext.containsBean("documentWorkflowServiceImpl"),
                "ApplicationContext should contain bean with name documentWorkflowServiceImpl");
    }

    @Test
    @DisplayName("Verify getPendingDocuments, getDocumentTimeline and logAction via injected Spring service")
    void testServiceMethods_getPendingDocuments_getTimeline_logAction() {
        UserLogin user = userLoginRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("UserLogin not found"));
        OrgNode org = orgNodeRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("OrgNode not found"));

        Document doc = Document.builder()
                .title("Tài liệu test context load")
                .documentTypeCd("CONG_VAN")
                .drafter(user)
                .issuingOrg(org)
                .status(DocumentStatus.SUBMITTED)
                .deleteFlg(false)
                .build();
        Document savedDoc = documentRepository.save(doc);

        // 1. Kiểm tra logAction
        documentWorkflowService.logAction(savedDoc.getId(), user.getUsername(), "CHUYEN_VIEN", "SUBMIT", "Tạo log qua service bean");

        // 2. Kiểm tra getDocumentTimeline
        List<WorkflowTimelineResponse> timeline = documentWorkflowService.getDocumentTimeline(savedDoc.getId());
        assertNotNull(timeline);
        assertFalse(timeline.isEmpty());
        assertEquals("Tạo log qua service bean", timeline.get(0).getComment());

        // 3. Kiểm tra getPendingDocuments
        List<PendingDocumentResponse> pendingList = documentWorkflowService.getPendingDocuments();
        assertNotNull(pendingList);
        assertTrue(pendingList.stream().anyMatch(p -> p.getId().equals(savedDoc.getId())));
        PendingDocumentResponse pendingDoc = pendingList.stream()
                .filter(p -> p.getId().equals(savedDoc.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals("Văn thư tiếp nhận", pendingDoc.getCurrentHolder(), "Trạng thái SUBMITTED phải ánh xạ tới 'Văn thư tiếp nhận'");
    }
}
