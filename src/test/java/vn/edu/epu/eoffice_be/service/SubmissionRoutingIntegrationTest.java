package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.PageRequest;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.SubmitApprovalRequest;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.event.*;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.security.pki.PdfSigner;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real service transactions and repositories, no outer test transaction and no OSIV. */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@Import(SubmissionRoutingIntegrationTest.CommittedEvents.class)
class SubmissionRoutingIntegrationTest {
    private static final String URL = "jdbc:h2:mem:submission-routing-" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL);
    }
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired UserLoginRepository users;
    @Autowired OrgNodeRepository orgs;
    @Autowired PositionAssignmentRepository positions;
    @Autowired DocumentRepository documents;
    @Autowired DocumentApprovalService submissions;
    @Autowired DocumentWorkflowService workflow;
    @Autowired JdbcTemplate jdbc;
    @Autowired CommittedEvents committed;
    @MockBean PdfSigner signer;
    @MockBean DocumentPdfStorageService pdfStorage;
    @MockBean DocumentDraftService drafts;
    TransactionTemplate tx;
    Long author, clerk, documentId, schoolId;

    public static class CommittedEvents {
        final AtomicInteger queues = new AtomicInteger();
        final AtomicInteger people = new AtomicInteger();
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void queue(DocumentQueuedForClerksEvent event) { queues.incrementAndGet(); }
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void person(DocumentSubmittedEvent event) { people.incrementAndGet(); }
    }

    @BeforeEach void fixtures() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            // This class has its own UUID H2 database; seeded assignments are unrelated to the test school.
            positions.deleteAll(); positions.flush();
            OrgNode school = orgs.saveAndFlush(OrgNode.builder().code("S-" + UUID.randomUUID())
                    .nameVn("Test School").nameEn("Test School").typeCd("UNIVERSITY").build());
            schoolId = school.getId();
            UserLogin rector = users.saveAndFlush(UserLogin.builder().username("ht-" + UUID.randomUUID())
                    .passwordHash("test").role(Role.LEADER).build());
            author = rector.getId();
            positions.saveAndFlush(PositionAssignment.builder().userLogin(rector).orgNode(school)
                    .positionCd("HT").effectiveFrom(Instant.now().minusSeconds(60)).build());
            clerk = users.saveAndFlush(UserLogin.builder().username("clerk-" + UUID.randomUUID())
                    .passwordHash("test").role(Role.CLERK).build()).getId();
            documentId = documents.saveAndFlush(Document.builder().drafter(rector).title("Submission integration")
                    .summary("Summary").content("Integration dossier").draftCode("DT-" + UUID.randomUUID()).documentTypeCd("QUYET_DINH")
                    .issuingOrgNode(school).issuingOrg(school).primaryFilePath("storage://1")
                    .status(DocumentStatus.DRAFT).build()).getId();
        });
        committed.queues.set(0); committed.people.set(0);
    }
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    void signing() {
        when(pdfStorage.readPdf("storage://1")).thenReturn(new byte[]{1});
        when(signer.signBytes(any(), eq(author.toString()), any(), any(), any(), any())).thenReturn(
                new PdfSigner.SignResult(author.toString(), "rector", "serial", "hash", new Date(), "field", null, new byte[]{2}));
        when(pdfStorage.storeSignedPdf(any(), any(), eq(documentId))).thenReturn("storage://2");
    }
    int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE document_id = ?", Integer.class, documentId);
    }

    @Test void rectorSubmissionCommitsToSharedClerkQueueAndPublishesAfterCommit() {
        var options = submissions.getSubmissionOptions(documentId, author);
        assertEquals("QUEUE", options.selectionMode().name());
        assertTrue(submissions.getEligibleApprovers(documentId, author).isEmpty());
        signing();
        submissions.submitForApproval(documentId, new SubmitApprovalRequest(), author);
        assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM documents WHERE id = ?", String.class, documentId));
        assertEquals("storage://2", jdbc.queryForObject("SELECT primary_file_path FROM documents WHERE id = ?", String.class, documentId));
        assertEquals(0, count("document_approval_requests"));
        assertEquals(1, count("document_workflow_logs"));
        assertEquals(1, count("document_process_logs"));
        assertEquals(1, committed.queues.get()); assertEquals(0, committed.people.get());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new JwtUserPrincipal(clerk, "clerk", "CLERK", true, List.of()), null, List.of()));
        assertTrue(workflow.getPendingPublishDocuments(PageRequest.of(0, 100)).getContent().stream()
                .anyMatch(p -> p.getId().equals(documentId)));
    }

    @Test void exceptionAfterWritesRollsBackQueueAndDoesNotDeliverEvent() {
        signing();
        when(drafts.mapToDocumentResponse(any())).thenThrow(new BusinessException(ErrorCode.BAD_REQUEST));
        assertThrows(BusinessException.class,
                () -> submissions.submitForApproval(documentId, new SubmitApprovalRequest(), author));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM documents WHERE id = ?", String.class, documentId));
        assertEquals("storage://1", jdbc.queryForObject("SELECT primary_file_path FROM documents WHERE id = ?", String.class, documentId));
        assertEquals(0, count("document_workflow_logs")); assertEquals(0, count("document_process_logs"));
        assertEquals(0, committed.queues.get()); assertEquals(0, committed.people.get());
    }

    @Test void realRepositoryFiltersExactUnitAndViceRectorTargetsOnlyRectorWithOsivOff() {
        Long vice = tx.execute(status -> {
            var u = users.saveAndFlush(UserLogin.builder().username("pht-" + UUID.randomUUID()).passwordHash("test")
                    .role(Role.LEADER).build());
            positions.saveAndFlush(PositionAssignment.builder().userLogin(u).orgNode(orgs.getReferenceById(schoolId))
                    .positionCd("PHT").effectiveFrom(Instant.now().minusSeconds(60)).build());
            Document document = documents.findById(documentId).orElseThrow();
            document.setDrafter(u); documents.saveAndFlush(document);
            return u.getId();
        });
        var options = submissions.getSubmissionOptions(documentId, vice);
        assertEquals("AUTO", options.selectionMode().name());
        assertEquals(author, options.routes().getFirst().approvers().getFirst().getUserId());
        tx.executeWithoutResult(status -> {
            var org = orgs.getReferenceById(schoolId);
            assertEquals(2, positions.findSubmissionCandidates(Set.of("HT", "PHT"), null, Instant.now()).size());
            assertEquals(2, positions.findSubmissionCandidates(Set.of("HT", "PHT"), org.getId(), Instant.now()).size());
            assertTrue(positions.findSubmissionCandidates(Set.of("HT", "PHT"), -999L, Instant.now()).isEmpty());
        });
        author = vice; signing();
        submissions.submitForApproval(documentId, new SubmitApprovalRequest(), vice);
        assertEquals("PENDING_APPROVAL", jdbc.queryForObject("SELECT status FROM documents WHERE id = ?", String.class, documentId));
        assertEquals(1, count("document_approval_requests"));
        assertEquals(1, committed.people.get()); assertEquals(0, committed.queues.get());
    }
}
