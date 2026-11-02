package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.SubmitApprovalRequest;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.event.*;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.security.pki.PdfSigner;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;
import vn.edu.epu.eoffice_be.workflow.WorkflowEngine;

import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentFirstSubmissionTest {
    @Mock DocumentRepository documents;
    @Mock DocumentAttachmentRepository attachments;
    @Mock DocumentApprovalRequestRepository approvals;
    @Mock DocumentProcessLogRepository processLogs;
    @Mock DocumentWorkflowLogRepository workflowLogs;
    @Mock UserLoginRepository users;
    @Mock UserInfoRepository profiles;
    @Mock PositionAssignmentRepository positions;
    @Mock PositionPermissionRepository permissions;
    @Mock PermissionResolver permissionResolver;
    @Mock ApplicationEventPublisher events;
    @Mock DocumentDraftService drafts;
    @Mock DocumentPdfStorageService pdfStorage;
    @Mock vn.edu.epu.eoffice_be.storage.FileStorageService storage;
    @Mock PdfSigner signer;
    DocumentApprovalService service;
    SubmissionRoutingService routing;
    WorkflowEngine engine;
    Document document;
    UserLogin sender;
    final Map<Long, UserLogin> accounts = new HashMap<>();
    final List<PositionAssignment> assignments = new ArrayList<>();
    OrgNode faculty = OrgNode.builder().id(100L).typeCd("FACULTY").nameVn("CNTT").build();
    OrgNode school = OrgNode.builder().id(1L).typeCd("UNIVERSITY").nameVn("EPU").build();

    @BeforeEach void setup() {
        routing = new SubmissionRoutingService(positions, profiles, vn.edu.epu.eoffice_be.service.TestDraftUnits.resolver(positions));
        engine = new WorkflowEngine(documents, workflowLogs, permissionResolver, routing);
        service = new DocumentApprovalService(documents, attachments, approvals, processLogs, users, profiles, positions, permissions, events, drafts, pdfStorage, storage, signer, routing, engine, org.mockito.Mockito.mock(vn.edu.epu.eoffice_be.service.DraftUnitResolver.class));
        lenient().when(users.findByIdAndDeleteFlgFalse(anyLong())).thenAnswer(inv -> Optional.ofNullable(accounts.get(inv.getArgument(0))));
        lenient().when(documents.findByIdAndDeleteFlgFalse(1L)).thenAnswer(inv -> Optional.of(document));
        lenient().when(positions.findActiveAssignments(anyLong(), any())).thenAnswer(inv -> assignments.stream()
                .filter(p -> p.getUserLogin().getId().equals(inv.getArgument(0))).toList());
        lenient().when(positions.findSubmissionCandidates(anyCollection(), nullable(Long.class), any()))
                .thenAnswer(inv -> List.copyOf(assignments));
        lenient().when(documents.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    UserLogin account(long id, Role role) {
        var user = UserLogin.builder().id(id).username("user" + id).role(role).build();
        accounts.put(id, user); return user;
    }
    void assign(UserLogin u, String cd, OrgNode org) {
        if (document != null && sender != null && sender.getId().equals(u.getId())) document.setIssuingOrgNode(org);
        assignments.add(PositionAssignment.builder().id((long) assignments.size() + 1).userLogin(u).orgNode(org)
                .positionCd(cd).effectiveFrom(Instant.now().minusSeconds(60)).build());
    }
    void prepare(Role role, String cd) {
        sender = account(10, role);
        document = Document.builder().id(1L).drafter(sender).title("Test").summary("Summary")
                .issuingOrgNode(faculty).primaryFilePath("storage://1").status(DocumentStatus.DRAFT).currentVersion(1).build();
        if (!cd.equals("NONE")) assign(sender, cd, Set.of("HT", "PHT").contains(cd) ? school : faculty);
        assign(account(20, Role.REVIEWER), "TK", faculty);
        if (!cd.equals("HT")) assign(account(30, Role.LEADER), "HT", school);
        if (!cd.equals("PHT")) assign(account(31, Role.LEADER), "PHT", school);
    }
    DocumentAttachment sourcePdf(byte[] bytes) {
        var main = DocumentAttachment.builder().id(101L).document(document).isMainDocument(true)
                .storagePath("source").storageFileName("main.pdf").fileExtension("PDF").build();
        main.setCreatedDate(Instant.now());
        when(attachments.findByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(1L)).thenReturn(List.of(main));
        when(storage.loadAsResource("source", "main.pdf"))
                .thenReturn(new org.springframework.core.io.ByteArrayResource(bytes));
        return main;
    }
    void signing() {
        sourcePdf(new byte[]{1, 2});
        when(signer.signBytes(any(), eq("10"), any(), any(), any(), any())).thenReturn(
                new PdfSigner.SignResult("10", "author", "serial", "hash", new Date(), "field", null, new byte[]{3, 4}));
        when(pdfStorage.storeSignedPdf(any(), any(), eq(1L))).thenReturn("storage://2");
    }
    void unchangedAndNoEffects() {
        assertEquals(DocumentStatus.DRAFT, document.getStatus());
        assertEquals(1, document.getCurrentVersion());
        assertNull(document.getSubmittedAt());
        assertEquals("storage://1", document.getPrimaryFilePath());
        verify(documents, never()).save(any());
        verifyNoInteractions(attachments, approvals, processLogs, workflowLogs, pdfStorage, storage, signer, events, drafts);
    }

    @ParameterizedTest
    @CsvSource({"USER,STAFF,20,REVIEW,IN_REVIEW", "REVIEWER,TK,31,LEADER_APPROVAL,PENDING_APPROVAL",
            "REVIEWER,PK,30,LEADER_APPROVAL,PENDING_APPROVAL", "REVIEWER,TP,30,LEADER_APPROVAL,PENDING_APPROVAL",
            "REVIEWER,PP,31,LEADER_APPROVAL,PENDING_APPROVAL", "CLERK,VT,30,LEADER_APPROVAL,PENDING_APPROVAL",
            "LEADER,PHT,,LEADER_APPROVAL,PENDING_APPROVAL", "LEADER,HT,,CLERK_QUEUE,APPROVED",
            "ADMIN,NONE,20,REVIEW,IN_REVIEW", "SYSTEM_ADMIN,NONE,31,LEADER_APPROVAL,PENDING_APPROVAL"})
    void routesPersistCorrectStatusIdentityLogsAndRecipient(Role role, String cd, Long recipient,
            SubmissionRoute route, DocumentStatus expected) {
        prepare(role, cd); signing();
        service.submitForApproval(1L, SubmitApprovalRequest.builder().route(route).approverUserId(recipient).build(), 10L);
        assertEquals(expected, document.getStatus());
        assertNotNull(document.getSubmittedAt());
        assertEquals("storage://2", document.getPrimaryFilePath());
        assertNull(document.getOfficialNumber()); assertNull(document.getOfficialFilePath());
        verify(documents).save(document);
        verify(workflowLogs).save(argThat(log -> log.getActor().getId().equals(10L)
                && log.getFromStatus() == DocumentStatus.DRAFT && log.getToStatus() == expected
                && "hash".equals(log.getSignatureHash())));
        verify(processLogs).save(argThat(log -> log.getToStatus().equals(expected.name())));
        verify(signer).signBytes(any(), eq("10"), any(), any(), any(), any());
        assertEquals(role, sender.getRole());
        if (expected == DocumentStatus.APPROVED) {
            verifyNoInteractions(approvals);
            verify(events).publishEvent(any(DocumentQueuedForClerksEvent.class));
            verify(events, never()).publishEvent(any(DocumentSubmittedEvent.class));
            verify(workflowLogs).save(argThat(log -> log.getComment().contains("phê duyệt nội dung")));
        } else {
            Long expectedRecipient = recipient == null ? 30L : recipient;
            verify(approvals).save(argThat(r -> r.getApproverUser().getId().equals(expectedRecipient)
                    && r.getStatus() == ApprovalRequestStatus.PENDING));
            verify(events).publishEvent(any(DocumentSubmittedEvent.class));
        }
    }

    @ParameterizedTest @EnumSource(Role.class)
    void everyRoleCannotReadOrSubmitAnotherAuthorsDocument(Role role) {
        prepare(role, "NONE"); document.setDrafter(account(99, Role.USER));
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class,
                () -> service.getSubmissionOptions(1L, 10L)).getErrorCode());
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class,
                () -> service.getEligibleApprovers(1L, 10L)).getErrorCode());
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class,
                () -> service.submitForApproval(1L, new SubmitApprovalRequest(), 10L)).getErrorCode());
        unchangedAndNoEffects();
    }

    @ParameterizedTest
    @CsvSource({"USER,STAFF,30,LEADER_APPROVAL", "USER,STAFF,31,REVIEW", "LEADER,PHT,20,LEADER_APPROVAL",
            "LEADER,HT,20,CLERK_QUEUE", "ADMIN,NONE,20,CLERK_QUEUE", "SYSTEM_ADMIN,NONE,20,LEADER_APPROVAL"})
    void wrongRouteOrRecipientHasNoSigningOrDatabaseSideEffects(Role role, String cd, Long recipient, SubmissionRoute route) {
        prepare(role, cd);
        assertThrows(BusinessException.class, () -> service.submitForApproval(1L,
                SubmitApprovalRequest.builder().route(route).approverUserId(recipient).build(), 10L));
        unchangedAndNoEffects();
    }

    @Test void optionsAndLegacyListUseSamePolicyAndChangedReceiverIsRejected() {
        prepare(Role.USER, "STAFF");
        assertEquals(20L, service.getSubmissionOptions(1L, 10L).routes().getFirst().approvers().getFirst().getUserId());
        assertEquals(20L, service.getEligibleApprovers(1L, 10L).getFirst().getUserId());
        assignments.stream().filter(p -> p.getUserLogin().getId() == 20).forEach(p -> p.setActualDismissalDate(Instant.now()));
        assertThrows(BusinessException.class, () -> service.submitForApproval(1L,
                SubmitApprovalRequest.builder().approverUserId(20L).build(), 10L));
        unchangedAndNoEffects();
    }

    @ParameterizedTest @ValueSource(strings = {"missingUnit", "ambiguous", "unsupported", "disabledRecipient", "crossUnit", "missingRector", "duplicateRector"})
    void invalidRoutingDataIsRejectedBeforeAnyPdfOrWrites(String problem) {
        prepare(Role.USER, "STAFF");
        Long recipient = 20L;
        switch (problem) {
            case "missingUnit" -> assignments.removeIf(p -> p.getUserLogin().getId().equals(sender.getId()));
            case "ambiguous" -> assign(sender, "STAFF", OrgNode.builder().id(999L).typeCd("DEPARTMENT").build());
            case "unsupported" -> assignments.stream().filter(p -> p.getUserLogin().getId().equals(sender.getId()))
                    .forEach(p -> p.setPositionCd("GĐTT"));
            case "disabledRecipient" -> accounts.get(20L).setUserStatusCd("DISABLED");
            case "crossUnit" -> assignments.stream().filter(p -> p.getUserLogin().getId() == 20)
                    .forEach(p -> p.setOrgNode(OrgNode.builder().id(999L).typeCd("FACULTY").build()));
            case "missingRector", "duplicateRector" -> {
                assignments.removeIf(p -> p.getUserLogin().getId().equals(sender.getId()));
                sender.setRole(Role.CLERK); recipient = 30L;
                if (problem.equals("missingRector")) assignments.removeIf(p -> "HT".equals(p.getPositionCd()));
                else assign(account(32, Role.LEADER), "HT", school);
            }
        }
        Long selected = recipient;
        assertThrows(BusinessException.class, () -> service.submitForApproval(1L,
                SubmitApprovalRequest.builder().approverUserId(selected).build(), 10L));
        unchangedAndNoEffects();
    }

    @Test void jwtAdminClaimDoesNotOverrideOrdinaryDatabaseRole() {
        prepare(Role.USER, "STAFF");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        new vn.edu.epu.eoffice_be.security.JwtUserPrincipal(10L, "user10", "SYSTEM_ADMIN", true, List.of()),
                        null, List.of()));
        try {
            assertThrows(BusinessException.class, () -> service.submitForApproval(1L,
                    SubmitApprovalRequest.builder().route(SubmissionRoute.LEADER_APPROVAL).approverUserId(30L).build(), 10L));
            unchangedAndNoEffects();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @ParameterizedTest @ValueSource(strings = {"missingPdf", "missingSigner", "signFailure", "emptyResult", "emptyHash", "storeFailure", "emptyReference"})
    void rectorNeverEntersQueueWithoutSuccessfulSigningAndStorage(String failure) {
        prepare(Role.LEADER, "HT");
        sourcePdf(failure.equals("missingPdf") ? new byte[0] : new byte[]{1});
        if (failure.equals("missingSigner")) {
            service = new DocumentApprovalService(documents, attachments, approvals, processLogs, users, profiles, positions, permissions, events, drafts, pdfStorage, storage, null, routing, engine, org.mockito.Mockito.mock(vn.edu.epu.eoffice_be.service.DraftUnitResolver.class));
        } else if (!failure.equals("missingPdf")) {
            if (failure.equals("signFailure")) when(signer.signBytes(any(), any(), any(), any(), any(), any()))
                    .thenThrow(new BusinessException(ErrorCode.PKI_STORAGE_ERROR));
            else when(signer.signBytes(any(), any(), any(), any(), any(), any())).thenReturn(
                    new PdfSigner.SignResult("10", "author", "serial", failure.equals("emptyHash") ? "" : "hash", new Date(),
                            "field", null, failure.equals("emptyResult") ? new byte[0] : new byte[]{2}));
            if (failure.equals("storeFailure")) when(pdfStorage.storeSignedPdf(any(), any(), any()))
                    .thenThrow(new BusinessException(ErrorCode.DOCUMENT_PDF_STORAGE_FAILED));
            if (failure.equals("emptyReference")) when(pdfStorage.storeSignedPdf(any(), any(), any())).thenReturn("");
        }
        assertThrows(BusinessException.class, () -> service.submitForApproval(1L, new SubmitApprovalRequest(), 10L));
        assertEquals(DocumentStatus.DRAFT, document.getStatus());
        assertNull(document.getSubmittedAt());
        assertEquals("storage://1", document.getPrimaryFilePath());
        verify(documents, never()).save(any());
        verifyNoInteractions(approvals, processLogs, workflowLogs, events, drafts);
    }

    @Test void engineIndependentlyRejectsAdminQueueAndMissingSignatureHash() {
        prepare(Role.ADMIN, "NONE");
        assertThrows(BusinessException.class, () -> engine.executeTransition(document,
                WorkflowAction.SUBMIT_TO_CLERK_QUEUE, sender, null, null, null, "hash"));
        unchangedAndNoEffects();
        sender.setRole(Role.LEADER); assign(sender, "HT", school);
        assignments.removeIf(p -> p.getUserLogin().getId() == 30);
        assertThrows(BusinessException.class, () -> engine.executeTransition(document,
                WorkflowAction.SUBMIT_TO_CLERK_QUEUE, sender, null, null, null, null));
        unchangedAndNoEffects();
    }

    @ParameterizedTest @EnumSource(Role.class)
    void engineDoesNotGrantQueueTransitionFromRoleAlone(Role role) {
        String cd = switch (role) {
            case USER -> "STAFF";
            case REVIEWER -> "TK";
            case LEADER -> "PHT";
            case CLERK -> "VT";
            default -> "NONE";
        };
        prepare(role, cd);
        assertThrows(BusinessException.class, () -> engine.executeTransition(document,
                WorkflowAction.SUBMIT_TO_CLERK_QUEUE, sender, null, null, null, "hash"));
        unchangedAndNoEffects();
    }

    @Test void resubmissionWithoutRecipientReturnsControlledErrorRatherThanNpe() {
        prepare(Role.USER, "STAFF"); document.setStatus(DocumentStatus.NEEDS_REVISION);
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.submitForApproval(1L, new SubmitApprovalRequest(), 10L)).getErrorCode());
        verifyNoInteractions(approvals, processLogs, workflowLogs, attachments, signer, storage, events);
    }

    @ParameterizedTest @ValueSource(strings = {"textOnly", "supplement", "deleted", "multiple", "notPdf"})
    void dossierCannotReplaceCurrentMainPdf(String problem) {
        prepare(Role.USER, "STAFF");
        document.setContent("Text alone is insufficient");
        var main = DocumentAttachment.builder().id(101L).isMainDocument(true).fileExtension("PDF").build();
        var list = switch (problem) {
            case "textOnly" -> List.<DocumentAttachment>of();
            case "supplement" -> { main.setMainDocument(false); yield List.of(main); }
            case "deleted" -> { main.setDeleteFlg(true); yield List.of(main); }
            case "multiple" -> List.of(main, DocumentAttachment.builder().isMainDocument(true).build());
            default -> { main.setFileExtension("DOCX"); yield List.of(main); }
        };
        when(attachments.findByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(1L)).thenReturn(list);
        assertThrows(BusinessException.class, () -> service.submitForApproval(1L,
                SubmitApprovalRequest.builder().approverUserId(20L).build(), 10L));
        verifyNoInteractions(signer, storage, pdfStorage, approvals, processLogs, workflowLogs, events);
        assertEquals(DocumentStatus.DRAFT, document.getStatus());
        assertEquals(1, document.getCurrentVersion());
    }

    @ParameterizedTest @ValueSource(strings = {"nullResult", "nullBytes", "emptyBytes", "nullHash", "blankHash", "nullReference", "blankReference", "signFailure", "storeFailure"})
    void ordinaryRouteAlsoRequiresSuccessfulSignatureAndStorage(String failure) {
        prepare(Role.USER, "STAFF");
        sourcePdf(new byte[]{1, 2});
        if (failure.equals("signFailure")) {
            when(signer.signBytes(any(), any(), any(), any(), any(), any()))
                    .thenThrow(new BusinessException(ErrorCode.PKI_STORAGE_ERROR));
        } else {
            var result = failure.equals("nullResult") ? null : new PdfSigner.SignResult("10", "author", "serial",
                    failure.equals("nullHash") ? null : failure.equals("blankHash") ? " " : "hash",
                    new Date(), "field", null, failure.equals("nullBytes") ? null : failure.equals("emptyBytes") ? new byte[0] : new byte[]{3});
            when(signer.signBytes(any(), any(), any(), any(), any(), any())).thenReturn(result);
            if (failure.equals("storeFailure")) when(pdfStorage.storeSignedPdf(any(), any(), any()))
                    .thenThrow(new BusinessException(ErrorCode.DOCUMENT_PDF_STORAGE_FAILED));
            if (failure.equals("nullReference") || failure.equals("blankReference"))
                when(pdfStorage.storeSignedPdf(any(), any(), any())).thenReturn(failure.equals("nullReference") ? null : " ");
        }
        assertThrows(BusinessException.class, () -> service.submitForApproval(1L,
                SubmitApprovalRequest.builder().approverUserId(20L).build(), 10L));
        assertEquals(DocumentStatus.DRAFT, document.getStatus());
        assertNull(document.getSubmittedAt());
        assertEquals("storage://1", document.getPrimaryFilePath());
        verify(documents, never()).save(any());
        verifyNoInteractions(approvals, processLogs, workflowLogs, events, drafts);
    }

    @ParameterizedTest @ValueSource(strings = {"stale", "equal", "unknownUpload", "unknownReturn"})
    void recalledLegacyDraftRequiresProvablyNewUploadBeforeReadingStorage(String problem) {
        prepare(Role.USER, "STAFF");
        Instant returned = Instant.now().minusSeconds(10);
        document.setSubmittedAt(returned.minusSeconds(10));
        var main = DocumentAttachment.builder().isMainDocument(true).fileExtension("PDF").build();
        main.setCreatedDate(problem.equals("unknownUpload") ? null : problem.equals("stale") ? returned.minusSeconds(1) : returned);
        when(attachments.findByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(1L)).thenReturn(List.of(main));
        when(attachments.findLatestPdfReturnAt(1L)).thenReturn(problem.equals("unknownReturn") ? Optional.empty() : Optional.of(returned));
        assertThrows(BusinessException.class, () -> service.submitForApproval(1L,
                SubmitApprovalRequest.builder().approverUserId(20L).build(), 10L));
        verifyNoInteractions(storage, pdfStorage, signer, approvals, workflowLogs, processLogs, events);
    }

    @Test void newMainIsSignedEvenWhenLegacyPrimaryReferenceStillPointsToV1() {
        prepare(Role.USER, "STAFF");
        Instant returned = Instant.now().minusSeconds(10);
        document.setSubmittedAt(returned.minusSeconds(10));
        signing();
        when(attachments.findLatestPdfReturnAt(1L)).thenReturn(Optional.of(returned));
        service.submitForApproval(1L, SubmitApprovalRequest.builder().approverUserId(20L).build(), 10L);
        verify(signer).signBytes(eq(new byte[]{1, 2}), eq("10"), any(), any(), any(), any());
        verify(pdfStorage, never()).readPdf(any());
        var order = inOrder(storage, signer, pdfStorage, documents, approvals);
        order.verify(storage).loadAsResource("source", "main.pdf");
        order.verify(signer).signBytes(any(), any(), any(), any(), any(), any());
        order.verify(pdfStorage).storeSignedPdf(any(), any(), any());
        order.verify(documents).save(document);
        order.verify(approvals).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "unreadable", "empty", "readFailure", "providerFailure"})
    void currentPdfReadFailureNeverFallsBackToOldSignedReference(String problem) throws Exception {
        prepare(Role.USER, "STAFF");
        var main=DocumentAttachment.builder().isMainDocument(true).fileExtension("PDF")
                .storagePath("source").storageFileName("v2.pdf").build();
        when(attachments.findByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(1L)).thenReturn(List.of(main));
        if (problem.equals("providerFailure")) {
            when(storage.loadAsResource("source","v2.pdf")).thenThrow(new IllegalStateException("provider unavailable"));
        } else if (!problem.equals("missing")) {
            org.springframework.core.io.Resource resource;
            if (problem.equals("empty")) resource=new org.springframework.core.io.ByteArrayResource(new byte[0]);
            else {
                resource=mock(org.springframework.core.io.Resource.class);
                when(resource.exists()).thenReturn(true);
                when(resource.isReadable()).thenReturn(!problem.equals("unreadable"));
                if (problem.equals("readFailure")) when(resource.getInputStream()).thenThrow(new java.io.IOException("read failed"));
            }
            when(storage.loadAsResource("source","v2.pdf")).thenReturn(resource);
        }
        assertThrows(BusinessException.class,()->service.submitForApproval(1L,
                SubmitApprovalRequest.builder().approverUserId(20L).build(),10L));
        verifyNoInteractions(signer,pdfStorage,approvals,processLogs,workflowLogs,events);
        assertEquals(DocumentStatus.DRAFT,document.getStatus());
        assertNull(document.getSubmittedAt());
    }

    @ParameterizedTest @EnumSource(Role.class)
    void disabledDatabaseSenderCannotReadOrSignPdf(Role role) {
        prepare(role,"NONE");sender.setUserStatusCd("DISABLED");
        assertEquals(ErrorCode.ACCOUNT_DISABLED,assertThrows(BusinessException.class,
                ()->service.submitForApproval(1L,SubmitApprovalRequest.builder().approverUserId(20L).build(),10L)).getErrorCode());
        unchangedAndNoEffects();
    }

    @Test void resubmissionRejectsDisabledRecipientBeforeAnyPdfOrSigning() {
        prepare(Role.USER,"STAFF");document.setStatus(DocumentStatus.NEEDS_REVISION);
        accounts.get(20L).setUserStatusCd("DISABLED");
        assertEquals(ErrorCode.APPROVER_NOT_ELIGIBLE,assertThrows(BusinessException.class,
                ()->service.submitForApproval(1L,SubmitApprovalRequest.builder().approverUserId(20L).build(),10L)).getErrorCode());
        assertEquals(DocumentStatus.NEEDS_REVISION,document.getStatus());
        assertEquals(1,document.getCurrentVersion());
        verifyNoInteractions(attachments,storage,pdfStorage,signer,approvals,processLogs,workflowLogs,events);
    }
}
