package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.*;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.security.pki.*;
import vn.edu.epu.eoffice_be.service.impl.DocumentWorkflowServiceImpl;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;
import vn.edu.epu.eoffice_be.workflow.WorkflowEngine;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentWorkflowSigningTest {
    @Mock DocumentRepository documents;
    @Mock DocumentWorkflowLogRepository logs;
    @Mock UserLoginRepository users;
    @Mock UserInfoRepository profiles;
    @Mock OrgNodeRepository orgs;
    @Mock WorkflowEngine engine;
    @Mock FileStorageService files;
    @Mock DocumentAttachmentRepository attachments;
    @Mock PdfSigner signer;
    @Mock DocumentPdfStorageService storage;
    @Mock DocumentDistributionRepository distributions;
    @Mock DocumentSignatureGuard guard;
    @Mock DocumentWorkingPdfService workingPdf;
    @InjectMocks DocumentWorkflowServiceImpl service;
    private UserLogin actor;
    private Document document;
    private final byte[] original = {1, 2};
    private final byte[] signed = {1, 2, 3};

    @BeforeEach
    void prepare() {
        actor = UserLogin.builder().id(1L).username("actor").role(Role.LEADER).build();
        document = Document.builder().id(50L).drafter(actor).status(DocumentStatus.IN_REVIEW)
                .primaryFilePath("storage://working").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new JwtUserPrincipal(1L, "actor", "LEADER", true, List.of()), null, List.of()));
        lenient().when(users.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(actor));
        lenient().when(documents.findByIdAndDeleteFlgFalse(50L)).thenReturn(Optional.of(document));
        lenient().when(workingPdf.read(document)).thenReturn(original);
        lenient().doAnswer(call -> {
            new WorkflowEngine(null, null, null).validateTransition(call.getArgument(0),
                    call.getArgument(1), call.getArgument(2));
            return null;
        }).when(engine).validateTransition(any(), any(), any());
        lenient().doAnswer(call -> {
            document.setStatus(new WorkflowEngine(null, null, null)
                    .getNextStatus(document.getStatus(), call.getArgument(1)));
            return null;
        }).when(engine).executeTransition(any(), any(), any(), any(), any(), any(), any());
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    private PdfSigner.SignResult result() {
        return new PdfSigner.SignResult("1", "actor", "serial", "signed-hash", new Date(), "field", null, signed);
    }

    private void initialSigning() {
        when(signer.signInitialBytes(any(), anyString(), anyString(), any(), any(), anyString(), any()))
                .thenReturn(result());
        when(storage.storeSignedPdf(eq(signed), anyString(), eq(50L))).thenReturn("storage://signed");
    }

    @ParameterizedTest
    @EnumSource(value = DocumentStatus.class, names = {"DRAFT", "NEEDS_REVISION", "REJECTED", "IN_REVIEW", "PENDING_APPROVAL", "APPROVED"})
    void initialSignKeepsStatusAndSelectsStageOnBackend(DocumentStatus status) {
        document.setStatus(status);
        initialSigning();
        service.initialSign(50L, DocumentInitialSignRequest.builder().build());
        assertEquals(status, document.getStatus());
        assertEquals("storage://signed", document.getPrimaryFilePath());
        verify(signer).signInitialBytes(eq(original), eq("1"), eq("actor"), any(), any(), anyString(),
                eq(InitialSignStage.fromStatus(status)));
        if (status == DocumentStatus.PENDING_APPROVAL) verify(guard).requireLeader(document, actor, original);
        verify(engine).executeTransition(document, WorkflowAction.INITIAL_SIGN, actor, null, null, null, "signed-hash");
    }

    @ParameterizedTest
    @EnumSource(value = DocumentStatus.class, names = {"PUBLISHED", "REVOKED"})
    void finalDocumentsCannotBeInitialSigned(DocumentStatus status) {
        document.setStatus(status);
        assertThrows(BusinessException.class, () -> service.initialSign(50L, DocumentInitialSignRequest.builder().build()));
        verifyNoInteractions(signer, storage);
    }

    @Test
    void leaderMustSignImageBeforeInitials() {
        document.setStatus(DocumentStatus.PENDING_APPROVAL);
        doThrow(new BusinessException(ErrorCode.BAD_REQUEST)).when(guard).requireLeader(document, actor, original);
        assertThrows(BusinessException.class, () -> service.initialSign(50L, DocumentInitialSignRequest.builder().build()));
        verifyNoInteractions(signer, storage);
        assertEquals(DocumentStatus.PENDING_APPROVAL, document.getStatus());
    }

    @Test
    void initialSigningStorageFailureDoesNotChangeWorkingReferenceOrTransition() {
        when(signer.signInitialBytes(any(), anyString(), anyString(), any(), any(), anyString(), any())).thenReturn(result());
        when(storage.storeSignedPdf(any(), anyString(), anyLong())).thenThrow(new BusinessException(ErrorCode.STORAGE_ERROR));
        assertThrows(BusinessException.class, () -> service.initialSign(50L, DocumentInitialSignRequest.builder().build()));
        assertEquals("storage://working", document.getPrimaryFilePath());
        verify(engine, never()).executeTransition(any(), any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"PKI_CA_NOT_INITIALIZED", "PKI_CA_EXPIRED", "PKI_STORAGE_ERROR"})
    void initialSigningPropagatesPkiErrorsWithoutTransition(ErrorCode code) {
        BusinessException failure = new BusinessException(code);
        when(signer.signInitialBytes(any(), anyString(), anyString(), any(), any(), anyString(), any())).thenThrow(failure);
        assertSame(failure, assertThrows(BusinessException.class,
                () -> service.initialSign(50L, DocumentInitialSignRequest.builder().build())));
        verifyNoInteractions(storage);
        verify(engine, never()).executeTransition(any(), any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = DocumentStatus.class, names = {"IN_REVIEW", "PENDING_APPROVAL"})
    void approveOnlyChecksSignaturesAndTransitionsWithoutSigning(DocumentStatus status) {
        document.setStatus(status);
        service.approve(50L, DocumentApproveRequest.builder().build());
        verify(guard).requireInitial(document, actor,
                status == DocumentStatus.IN_REVIEW ? InitialSignStage.REVIEWER : InitialSignStage.LEADER, original);
        assertEquals(status == DocumentStatus.IN_REVIEW ? DocumentStatus.PENDING_APPROVAL : DocumentStatus.APPROVED,
                document.getStatus());
        verifyNoInteractions(signer, storage);
    }

    @ParameterizedTest
    @EnumSource(value = DocumentStatus.class, names = {"IN_REVIEW", "PENDING_APPROVAL"})
    void missingInitialsBlockTransition(DocumentStatus status) {
        document.setStatus(status);
        doThrow(new BusinessException(ErrorCode.BAD_REQUEST)).when(guard).requireInitial(any(), any(), any(), any());
        assertThrows(BusinessException.class, () -> service.approve(50L, DocumentApproveRequest.builder().build()));
        assertEquals(status, document.getStatus());
        verifyNoInteractions(signer, storage);
        verify(engine, never()).executeTransition(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void signingThenApprovingDoesNotAttemptToSignTwice() {
        initialSigning();
        service.initialSign(50L, DocumentInitialSignRequest.builder().build());
        service.approve(50L, DocumentApproveRequest.builder().build());
        verify(signer, times(1)).signInitialBytes(any(), anyString(), anyString(), any(), any(), anyString(), any());
        assertEquals(DocumentStatus.PENDING_APPROVAL, document.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = DocumentStatus.class, names = {"DRAFT", "APPROVED", "PUBLISHED"})
    void approveRejectsInvalidState(DocumentStatus status) {
        document.setStatus(status);
        assertThrows(BusinessException.class, () -> service.approve(50L, DocumentApproveRequest.builder().build()));
        verifyNoInteractions(signer, guard, storage);
    }

    @Test
    void leaderImageSignUsesExistingDefaultPositionAndAssignsLeaderOnlyAfterSuccess() {
        document.setStatus(DocumentStatus.PENDING_APPROVAL);
        when(signer.signVisibleBytes(any(), anyString(), anyString(), any(), any(), anyString(), any())).thenReturn(result());
        when(storage.storeSignedPdf(any(), anyString(), anyLong())).thenReturn("storage://leader");
        service.leaderSign(50L, DocumentLeaderSignRequest.builder().build());
        ArgumentCaptor<SignaturePosition> position = ArgumentCaptor.forClass(SignaturePosition.class);
        verify(signer).signVisibleBytes(eq(original), eq("1"), eq("actor"), any(), any(), anyString(), position.capture());
        assertEquals(new SignaturePosition(9999, 380f, 120f, 150f, 80f), position.getValue());
        assertEquals(actor, document.getSigner());
        assertEquals(DocumentStatus.PENDING_APPROVAL, document.getStatus());
        verify(engine).executeTransition(document, WorkflowAction.LEADER_SIGN, actor, null, null, null, "signed-hash");
    }

    @Test
    void leaderImageSignUsesFrontendCoordinates() {
        document.setStatus(DocumentStatus.PENDING_APPROVAL);
        when(signer.signVisibleBytes(any(), anyString(), anyString(), any(), any(), anyString(), any())).thenReturn(result());
        when(storage.storeSignedPdf(any(), anyString(), anyLong())).thenReturn("storage://leader");
        service.leaderSign(50L, DocumentLeaderSignRequest.builder().signaturePage(2).signatureX(40f).signatureY(60f)
                .signatureWidth(90f).signatureHeight(30f).build());
        verify(signer).signVisibleBytes(eq(original), eq("1"), eq("actor"), any(), any(), anyString(),
                eq(new SignaturePosition(2, 40f, 60f, 90f, 30f)));
    }

    @Test
    void failedLeaderSignDoesNotAssignSigner() {
        document.setStatus(DocumentStatus.PENDING_APPROVAL);
        when(signer.signVisibleBytes(any(), anyString(), anyString(), any(), any(), anyString(), any()))
                .thenThrow(new BusinessException(ErrorCode.PKI_CA_NOT_INITIALIZED));
        assertThrows(BusinessException.class, () -> service.leaderSign(50L, DocumentLeaderSignRequest.builder().build()));
        assertNull(document.getSigner());
        assertEquals("storage://working", document.getPrimaryFilePath());
        verifyNoInteractions(storage);
    }

    private void preparePublication() {
        document.setStatus(DocumentStatus.APPROVED);
        document.setDocumentBook(DocumentBook.builder().id(10L).build());
        document.setOfficialNumber("1/CV-EPU");
        document.setSigner(UserLogin.builder().id(2L).username("rector").role(Role.LEADER).build());
        actor.setRole(Role.CLERK);
    }

    @Test
    void clerkPublicationPreservesLeaderAndOnlyAddsFinalSeal() {
        preparePublication();
        when(signer.signAndCertifyBytes(any(), anyString(), anyString(), any(), any(), anyString(), any())).thenReturn(result());
        when(storage.storeSignedPdf(any(), anyString(), anyLong())).thenReturn("storage://official");
        when(files.computeSha256(signed)).thenReturn("final-hash");
        service.signAndPublish(50L, DocumentPublishRequest.builder().distributionScope(DistributionScope.ALL).build());
        assertEquals(2L, document.getSigner().getId());
        assertEquals(DocumentStatus.PUBLISHED, document.getStatus());
        verify(guard).requireLeader(document, document.getSigner(), original);
        verify(guard).requireInitial(document, document.getSigner(), InitialSignStage.LEADER, original);
        verify(guard).requireInitial(document, actor, InitialSignStage.CLERK, original);
        verify(signer).signAndCertifyBytes(eq(original), eq("1"), anyString(), any(), any(), anyString(), any());
        verify(signer, never()).signInitialBytes(any(), anyString(), anyString(), any(), any(), anyString(), any());
        verify(distributions).save(any(DocumentDistribution.class));
    }

    @Test
    void publicationRequiresAssignedBookAndNumber() {
        document.setStatus(DocumentStatus.APPROVED);
        assertThrows(BusinessException.class, () -> service.signAndPublish(50L,
                DocumentPublishRequest.builder().distributionScope(DistributionScope.ALL).build()));
        verifyNoInteractions(signer, storage, distributions);
    }

    @Test
    void unsignedClerkCannotPublish() {
        preparePublication();
        lenient().doThrow(new BusinessException(ErrorCode.BAD_REQUEST)).when(guard)
                .requireInitial(document, actor, InitialSignStage.CLERK, original);
        assertThrows(BusinessException.class, () -> service.signAndPublish(50L,
                DocumentPublishRequest.builder().distributionScope(DistributionScope.ALL).build()));
        verifyNoInteractions(signer, storage, distributions);
        assertEquals(DocumentStatus.APPROVED, document.getStatus());
    }

    @Test
    void invalidEffectiveDateCannotPublish() {
        Instant now = Instant.now();
        assertThrows(BusinessException.class, () -> service.signAndPublish(50L, DocumentPublishRequest.builder()
                .distributionScope(DistributionScope.ALL).issueDate(now).effectiveDate(now.minusSeconds(1)).build()));
        verifyNoInteractions(signer, guard, storage, distributions);
    }
}
