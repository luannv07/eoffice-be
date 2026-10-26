package vn.edu.epu.eoffice_be.security.pki;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.service.DocumentSignatureGuard;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowSignatureProofTest {
    @TempDir Path directory;
    private PdfSigner signer;
    private PdfSignatureVerifier verifier;
    private DocumentSignatureGuard guard;
    private Document document;
    private UserLogin author;
    private final List<DocumentWorkflowLog> records = new ArrayList<>();

    @BeforeEach
    void prepare() throws Exception {
        var fixture = PkiTestFixture.create(directory);
        signer = fixture.signer();
        verifier = fixture.verifier();
        var logs = mock(DocumentWorkflowLogRepository.class);
        when(logs.findByDocumentIdAndDeleteFlgFalseOrderByCreatedDateAsc(50L)).thenReturn(records);
        var process = mock(DocumentProcessLogRepository.class);
        guard = new DocumentSignatureGuard(verifier, logs, process);
        document = Document.builder().id(50L).status(DocumentStatus.DRAFT).primaryFilePath("storage://current").build();
        author = UserLogin.builder().id(1L).username("author").build();
    }

    private byte[] blankPdf() throws Exception {
        try (var pdf = new PDDocument(); var output = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage());
            pdf.save(output);
            return output.toByteArray();
        }
    }

    private byte[] initial(byte[] pdf, UserLogin actor, InitialSignStage stage, DocumentStatus status) {
        var signed = signer.signInitialBytes(pdf, actor.getId().toString(), actor.getUsername(), null, null,
                "Initial " + stage, stage);
        records.add(DocumentWorkflowLog.builder().document(document).actor(actor).action(WorkflowAction.INITIAL_SIGN)
                .fromStatus(status).toStatus(status).signatureHash(signed.outPdfSha256()).build());
        return signed.signedPdfBytes();
    }

    @Test
    void sameUserCanSignMultipleStagesWithoutDuplicateAndEarlierInitialsRemainValid() throws Exception {
        byte[] drafter = initial(blankPdf(), author, InitialSignStage.DRAFTER, DocumentStatus.DRAFT);
        byte[] reviewer = initial(drafter, author, InitialSignStage.REVIEWER, DocumentStatus.IN_REVIEW);
        assertDoesNotThrow(() -> guard.requireInitial(document, author, InitialSignStage.DRAFTER, reviewer));
        assertDoesNotThrow(() -> guard.requireInitial(document, author, InitialSignStage.REVIEWER, reviewer));
        assertTrue(verifier.verify(reviewer).getSignatures().stream().allMatch(s -> s.isFullyValid()));
        assertThrows(DuplicateSignatureException.class, () -> initial(reviewer, author,
                InitialSignStage.REVIEWER, DocumentStatus.IN_REVIEW));
    }

    @Test
    void leaderImageThenInitialsThenDifferentClerkAndFinalSealPreserveAllSignatures() throws Exception {
        byte[] draft = initial(blankPdf(), author, InitialSignStage.DRAFTER, DocumentStatus.DRAFT);
        byte[] reviewed = initial(draft, author, InitialSignStage.REVIEWER, DocumentStatus.IN_REVIEW);
        var visible = signer.signVisibleBytes(reviewed, "1", "Rector", null, null,
                "Leader approval", new SignaturePosition(9999, 380f, 120f, 150f, 80f));
        records.add(DocumentWorkflowLog.builder().document(document).actor(author).action(WorkflowAction.LEADER_SIGN)
                .fromStatus(DocumentStatus.PENDING_APPROVAL).toStatus(DocumentStatus.PENDING_APPROVAL)
                .signatureHash(visible.outPdfSha256()).build());
        byte[] approved = initial(visible.signedPdfBytes(), author, InitialSignStage.LEADER, DocumentStatus.PENDING_APPROVAL);
        UserLogin clerk = UserLogin.builder().id(2L).username("clerk").build();
        byte[] numbered = initial(approved, clerk, InitialSignStage.CLERK, DocumentStatus.APPROVED);
        guard.requireLeader(document, author, numbered);
        guard.requireInitial(document, author, InitialSignStage.LEADER, numbered);
        guard.requireInitial(document, clerk, InitialSignStage.CLERK, numbered);
        var sealed = signer.signAndCertifyBytes(numbered, "2", "Clerk", null, null, "Publication",
                new SignaturePosition(1, 350f, 150f, 180f, 60f));
        var result = verifier.verify(sealed.signedPdfBytes());
        assertEquals(6, result.getTotalSignatures());
        assertTrue(result.getSignatures().stream().allMatch(s -> s.isFullyValid()), result.getSummaryMessage());
        assertTrue(result.getSignatures().stream().anyMatch(s -> s.getFieldName().equals("sig_approve_1")));
    }

    @Test
    void wrongActorOrStageCannotReuseSignature() throws Exception {
        byte[] pdf = initial(blankPdf(), author, InitialSignStage.DRAFTER, DocumentStatus.DRAFT);
        UserLogin other = UserLogin.builder().id(2L).build();
        assertThrows(BusinessException.class, () -> guard.requireInitial(document, other, InitialSignStage.DRAFTER, pdf));
        assertThrows(BusinessException.class, () -> guard.requireInitial(document, author, InitialSignStage.REVIEWER, pdf));
        assertThrows(BusinessException.class, () -> guard.requireLeader(document, author, pdf));
    }

    @Test
    void historicalLogDoesNotAuthorizeUnsignedReplacement() throws Exception {
        initial(blankPdf(), author, InitialSignStage.DRAFTER, DocumentStatus.DRAFT);
        byte[] replacement = blankPdf();
        assertThrows(BusinessException.class, () -> guard.requireInitial(document, author, InitialSignStage.DRAFTER, replacement));
    }

    @Test
    void uploadedSignedPdfWithoutWorkingReferenceIsNotProofOfSigningCurrentVersion() throws Exception {
        byte[] signed = initial(blankPdf(), author, InitialSignStage.DRAFTER, DocumentStatus.DRAFT);
        document.setPrimaryFilePath(null);
        assertThrows(BusinessException.class, () -> guard.requireInitial(document, author, InitialSignStage.DRAFTER, signed));
    }

    @Test
    void signatureWithoutMatchingHistoryIsRejected() throws Exception {
        byte[] pdf = initial(blankPdf(), author, InitialSignStage.DRAFTER, DocumentStatus.DRAFT);
        records.clear();
        assertThrows(BusinessException.class, () -> guard.requireInitial(document, author, InitialSignStage.DRAFTER, pdf));
    }

    @Test
    void appendedTamperingInvalidatesTransitionEvenWhenRequiredSignatureExists() throws Exception {
        byte[] pdf = initial(blankPdf(), author, InitialSignStage.DRAFTER, DocumentStatus.DRAFT);
        byte[] tampered = Arrays.copyOf(pdf, pdf.length + 5);
        assertThrows(BusinessException.class, () -> guard.requireInitial(document, author, InitialSignStage.DRAFTER, tampered));
    }

    @Test
    void legacySignatureCountsOnlyWithMatchingStageAndRevisionHash() throws Exception {
        var legacy = signer.signBytes(blankPdf(), "1", "Author", "Legacy initials");
        records.add(DocumentWorkflowLog.builder().document(document).actor(author).action(WorkflowAction.INITIAL_SIGN)
                .fromStatus(DocumentStatus.IN_REVIEW).toStatus(DocumentStatus.IN_REVIEW)
                .signatureHash(legacy.outPdfSha256()).build());
        assertDoesNotThrow(() -> guard.requireInitial(document, author, InitialSignStage.REVIEWER, legacy.signedPdfBytes()));
        assertThrows(BusinessException.class, () -> guard.requireInitial(document, author, InitialSignStage.DRAFTER, legacy.signedPdfBytes()));
    }
}
