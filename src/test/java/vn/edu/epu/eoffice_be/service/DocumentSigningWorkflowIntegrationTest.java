package vn.edu.epu.eoffice_be.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.transaction.TestTransaction;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.*;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.security.pki.*;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real workflow, SQL persistence and cryptographic proofs; only storage and PKI infrastructure are isolated. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:separate-signing-flow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@ActiveProfiles("test")
@Transactional
class DocumentSigningWorkflowIntegrationTest {
    @Autowired DocumentWorkflowService workflow;
    @Autowired DocumentApprovalService approval;
    @Autowired DocumentBookService numbering;
    @Autowired DocumentRepository documents;
    @Autowired UserLoginRepository users;
    @Autowired OrgNodeRepository orgs;
    @Autowired DocumentBookRepository books;
    @SpyBean DocumentWorkflowLogRepository logs;
    @Autowired DocumentDistributionRepository distributions;
    @MockBean PermissionResolver permissions;
    @MockBean PdfSigner signer;
    @MockBean PdfSignatureVerifier verifier;
    @MockBean DocumentPdfStorageService storage;
    @TempDir Path directory;
    private final Map<String, byte[]> pdfs = new HashMap<>();
    private UserLogin author, reviewer, rector, clerk;
    private Document document;
    private DocumentBook book;
    private PdfSignatureVerifier realVerifier;

    @BeforeEach
    void prepare() throws Exception {
        author = user("author", Role.USER);
        reviewer = user("reviewer", Role.ADMIN);
        rector = user("rector", Role.LEADER);
        clerk = user("clerk", Role.CLERK);
        authenticate(author);
        OrgNode org = orgs.findAll().get(0);
        var sampleBook = books.findAll().get(0);
        String code = UUID.randomUUID().toString().substring(0, 8);
        book = books.saveAndFlush(DocumentBook.builder().bookCode("WF-" + code).bookName("Workflow test")
                .year(LocalDate.now().getYear()).bookLevel(1).orgNode(org).documentType(sampleBook.getDocumentType())
                .numberPattern("WF-" + code + "-{NUM}").currentNumber(0).isClosed(false).build());
        document = documents.saveAndFlush(Document.builder().title("Separate signing flow").summary("Summary")
                .content("Content").drafter(author).creatorUser(author).issuingOrg(org).signer(rector)
                .documentTypeCd(book.getDocumentType().getItemCd()).status(DocumentStatus.DRAFT)
                .primaryFilePath("storage://source").build());
        try (var pdf = new PDDocument(); var output = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage());
            pdf.save(output);
            pdfs.put("storage://source", output.toByteArray());
        }
        var pki = PkiTestFixture.create(directory);
        PdfSigner actual = pki.signer();
        realVerifier = pki.verifier();
        when(verifier.verify(any(byte[].class))).thenAnswer(call -> realVerifier.verify(call.getArgument(0, byte[].class)));
        when(signer.signInitialBytes(any(), anyString(), anyString(), any(), any(), anyString(), any()))
                .thenAnswer(call -> actual.signInitialBytes(call.getArgument(0), call.getArgument(1), call.getArgument(2),
                        call.getArgument(3), call.getArgument(4), call.getArgument(5), call.getArgument(6)));
        when(signer.signVisibleBytes(any(), anyString(), anyString(), any(), any(), anyString(), any()))
                .thenAnswer(call -> actual.signVisibleBytes(call.getArgument(0), call.getArgument(1), call.getArgument(2),
                        call.getArgument(3), call.getArgument(4), call.getArgument(5), call.getArgument(6)));
        when(signer.signAndCertifyBytes(any(), anyString(), anyString(), any(), any(), anyString(), any()))
                .thenAnswer(call -> actual.signAndCertifyBytes(call.getArgument(0), call.getArgument(1), call.getArgument(2),
                        call.getArgument(3), call.getArgument(4), call.getArgument(5), call.getArgument(6)));
        when(storage.readPdf(anyString())).thenAnswer(call -> pdfs.get(call.getArgument(0)));
        when(storage.storeSignedPdf(any(), anyString(), anyLong())).thenAnswer(call -> {
            String reference = "storage://signed-" + pdfs.size();
            pdfs.put(reference, call.getArgument(0));
            return reference;
        });
        when(permissions.hasPermission(anyLong(), any(Role.class), anyString(), anyLong())).thenAnswer(call -> {
            Long user = call.getArgument(0);
            String permission = call.getArgument(2);
            return (user.equals(reviewer.getId()) && permission.equals("DOCUMENT_APPROVE"))
                    || (user.equals(rector.getId()) && (permission.equals("DOCUMENT_APPROVE")
                    || permission.equals("DOCUMENT_SIGN_OFFICIAL")));
        });
    }

    private UserLogin user(String prefix, Role role) {
        return users.saveAndFlush(UserLogin.builder().username(prefix + UUID.randomUUID().toString().substring(0, 8))
                .passwordHash("synthetic-test-password").role(role).build());
    }

    private void authenticate(UserLogin actor) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new JwtUserPrincipal(actor.getId(), actor.getUsername(), actor.getRole().name(), true, List.of()),
                null, List.of(new SimpleGrantedAuthority("ROLE_" + actor.getRole()))));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void completeFlowKeepsEverySignatureAndSeparatesEachTransition() {
        workflow.initialSign(document.getId(), DocumentInitialSignRequest.builder().build());
        assertEquals(DocumentStatus.DRAFT, document.getStatus());
        String drafterPdf = document.getPrimaryFilePath();
        approval.submitForApproval(document.getId(), SubmitApprovalRequest.builder().approverUserId(reviewer.getId()).build(), author.getId());
        assertEquals(DocumentStatus.IN_REVIEW, document.getStatus());
        assertEquals(drafterPdf, document.getPrimaryFilePath());

        authenticate(reviewer);
        workflow.initialSign(document.getId(), DocumentInitialSignRequest.builder().build());
        assertEquals(DocumentStatus.IN_REVIEW, document.getStatus());
        String reviewerPdf = document.getPrimaryFilePath();
        workflow.approve(document.getId(), DocumentApproveRequest.builder().build());
        assertEquals(DocumentStatus.PENDING_APPROVAL, document.getStatus());
        assertEquals(reviewerPdf, document.getPrimaryFilePath());

        authenticate(rector);
        workflow.leaderSign(document.getId(), DocumentLeaderSignRequest.builder().build());
        assertEquals(DocumentStatus.PENDING_APPROVAL, document.getStatus());
        workflow.initialSign(document.getId(), DocumentInitialSignRequest.builder().build());
        assertEquals(DocumentStatus.PENDING_APPROVAL, document.getStatus());
        String leaderPdf = document.getPrimaryFilePath();
        workflow.approve(document.getId(), DocumentApproveRequest.builder().build());
        assertEquals(DocumentStatus.APPROVED, document.getStatus());
        assertEquals(leaderPdf, document.getPrimaryFilePath());

        authenticate(clerk);
        AssignDocumentNumberRequest numberRequest = new AssignDocumentNumberRequest();
        numberRequest.setBookId(book.getId());
        numberRequest.setDocumentId(document.getId());
        numberRequest.setEffectiveDate(LocalDate.of(book.getYear(), 1, 1));
        numbering.assignNumber(numberRequest);
        assertEquals(leaderPdf, document.getPrimaryFilePath());
        assertEquals(DocumentStatus.APPROVED, document.getStatus());
        workflow.initialSign(document.getId(), DocumentInitialSignRequest.builder().build());
        assertEquals(DocumentStatus.APPROVED, document.getStatus());
        workflow.signAndPublish(document.getId(), DocumentPublishRequest.builder()
                .distributionScope(DistributionScope.SINGLE_ORG).recipientOrgIds(List.of(document.getOrgId())).build());

        assertEquals(DocumentStatus.PUBLISHED, document.getStatus());
        assertEquals(rector.getId(), document.getSigner().getId());
        var verified = realVerifier.verify(pdfs.get(document.getOfficialFilePath()));
        assertEquals(6, verified.getTotalSignatures());
        assertTrue(verified.getSignatures().stream().allMatch(s -> s.isFullyValid()), verified.getSummaryMessage());
        assertEquals(1, distributions.findByDocumentId(document.getId()).size());
        assertTrue(documents.findAccessiblePublishedDocument(document.getId(), document.getOrgId()).isPresent());
        var history = logs.findByDocumentIdAndDeleteFlgFalseOrderByCreatedDateAsc(document.getId());
        assertEquals(4, history.stream().filter(log -> log.getAction() == WorkflowAction.INITIAL_SIGN).count());
        assertTrue(history.stream().filter(log -> log.getAction() == WorkflowAction.INITIAL_SIGN
                || log.getAction() == WorkflowAction.LEADER_SIGN).allMatch(log -> log.getFromStatus() == log.getToStatus()));
        verify(signer, never()).signBytes(any(), anyString(), anyString(), anyString());
    }

    @Test
    void cannotSubmitUnsignedCurrentPdf() {
        assertThrows(BusinessException.class, () -> approval.submitForApproval(document.getId(),
                SubmitApprovalRequest.builder().approverUserId(reviewer.getId()).build(), author.getId()));
        assertEquals(DocumentStatus.DRAFT, document.getStatus());
        verifyNoInteractions(signer);
    }

    @Test
    void failedHistoryWriteRollsBackWorkingPdfAndStateInDatabase() {
        Long id = document.getId();
        TestTransaction.flagForCommit();
        TestTransaction.end();
        doThrow(new org.springframework.dao.DataIntegrityViolationException("test history failure"))
                .when(logs).save(any(DocumentWorkflowLog.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> workflow.initialSign(id, DocumentInitialSignRequest.builder().build()));

        Document unchanged = documents.findByIdAndDeleteFlgFalse(id).orElseThrow();
        assertEquals(DocumentStatus.DRAFT, unchanged.getStatus());
        assertEquals("storage://source", unchanged.getPrimaryFilePath());
        assertTrue(logs.findByDocumentIdAndDeleteFlgFalseOrderByCreatedDateAsc(id).isEmpty());
        documents.deleteById(id);
    }
}
