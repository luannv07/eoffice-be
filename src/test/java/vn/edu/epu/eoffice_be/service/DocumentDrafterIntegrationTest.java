package vn.edu.epu.eoffice_be.service;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.OrgNodeRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** No test-wide transaction: exercise response mapping with OSIV disabled. */
@SpringBootTest
@ActiveProfiles("test")
class DocumentDrafterIntegrationTest {
    // @MockBean creates a separate context. Its Liquibase run must not reuse the
    // shared test-profile database while other cached contexts still hold it open.
    private static final String DATABASE_URL = "jdbc:h2:mem:document-drafter-" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired PlatformTransactionManager transactions;
    @Autowired DocumentRepository documents;
    @Autowired UserLoginRepository users;
    @Autowired OrgNodeRepository orgs;
    @Autowired DocumentQueryService query;
    @Autowired DocumentPublishService publishing;
    @MockBean DocumentPdfStorageService pdfStorage;

    @Test
    void authorResponsesRemainReadableOutsideRepositoryTransactions() {
        TransactionTemplate tx = new TransactionTemplate(transactions);
        Long id = tx.execute(status -> {
            var user = users.findAll().getFirst();
            var org = orgs.findAll().getFirst();
            return documents.saveAndFlush(Document.builder().drafter(user).issuingOrg(org).issuingOrgNode(org)
                    .draftCode("DT-" + UUID.randomUUID()).title("Author integration test")
                    .summary("Test summary").documentTypeCd("CONG_VAN").status(DocumentStatus.APPROVED)
                    .officialFilePath("test-author.pdf").build()).getId();
        });
        try {
            when(pdfStorage.readPdf("test-author.pdf")).thenReturn(new byte[]{1, 2, 3});
            Document published = publishing.publishDocument(id, null);
            assertTrue(Hibernate.isInitialized(published.getDrafter()));
            assertNotNull(published.getDrafter().getUsername());
            Map<?, ?> detail = (Map<?, ?>) query.getDocumentDetailsForAdmin(id);
            assertEquals(published.getDrafter().getId(), detail.get("drafterId"));
            assertEquals(published.getDrafter().getUsername(), detail.get("drafterUsername"));
            assertNotNull(detail.get("drafterFullName"));
            DocumentResponse response = query.getAllPublishedDocuments().stream()
                    .filter(d -> d.getId().equals(id)).findFirst().orElseThrow();
            assertEquals(published.getDrafter().getId(), response.getDrafterId());
            assertEquals(published.getDrafter().getUsername(), response.getDrafterUsername());
            assertNotNull(response.getDrafterFullName());
        } finally {
            tx.executeWithoutResult(status -> documents.deleteById(id));
        }
    }
}
