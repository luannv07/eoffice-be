package vn.edu.epu.eoffice_be.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.common.exception.GlobalExceptionHandler;
import vn.edu.epu.eoffice_be.controller.DocumentController;
import vn.edu.epu.eoffice_be.dto.request.SubmitApprovalRequest;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.security.pki.PdfSigner;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;
import vn.edu.epu.eoffice_be.workflow.WorkflowEngine;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DocumentSubmissionOwnershipTest {
    @Mock DocumentRepository documents;
    @Mock DocumentAttachmentRepository attachments;
    @Mock DocumentApprovalRequestRepository approvals;
    @Mock DocumentProcessLogRepository logs;
    @Mock UserLoginRepository users;
    @Mock UserInfoRepository profiles;
    @Mock PositionAssignmentRepository positions;
    @Mock PositionPermissionRepository positionPermissions;
    @Mock ApplicationEventPublisher events;
    @Mock DocumentDraftService drafts;
    @Mock DocumentPdfStorageService pdfStorage;
    @Mock vn.edu.epu.eoffice_be.storage.FileStorageService storage;
    @Mock PdfSigner signer;
    @InjectMocks DocumentApprovalService service;

    private UserLogin sender(Role role) {
        return UserLogin.builder().id(100L).username("author").role(role).build();
    }

    private Document fixture(UserLogin sender, UserLogin drafter) {
        Document document = Document.builder().id(1L).drafter(drafter).status(DocumentStatus.NEEDS_REVISION)
                .title("Title").summary("Summary").currentVersion(4).primaryFilePath("storage://10").build();
        when(users.findByIdAndDeleteFlgFalse(100L)).thenReturn(Optional.of(sender));
        when(documents.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(document));
        return document;
    }

    private SubmitApprovalRequest request() {
        return SubmitApprovalRequest.builder().approverUserId(200L).build();
    }

    private void assertUnchangedAndNoSideEffects(Document document) {
        assertEquals(DocumentStatus.NEEDS_REVISION, document.getStatus());
        assertEquals(4, document.getCurrentVersion());
        assertEquals("storage://10", document.getPrimaryFilePath());
        assertNull(document.getSubmittedAt());
        verify(documents, never()).save(any());
        verifyNoInteractions(attachments, approvals, logs, profiles, positions, positionPermissions,
                events, drafts, pdfStorage, storage, signer);
    }

    @ParameterizedTest @EnumSource(Role.class)
    void onlyDrafterCanSubmitRegardlessOfRole(Role role) {
        Document document = fixture(sender(role), UserLogin.builder().id(999L).build());
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submitForApproval(1L, request(), 100L));
        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertUnchangedAndNoSideEffects(document);
    }

    @Test
    void missingDrafterIsForbiddenRatherThanNullPointer() {
        Document document = fixture(sender(Role.ADMIN), null);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submitForApproval(1L, request(), 100L));
        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertUnchangedAndNoSideEffects(document);
    }

    @ParameterizedTest @EnumSource(Role.class)
    void drafterSignsWithOwnDatabaseIdentityForEveryRole(Role role) {
        UserLogin actor = sender(role);
        Document document = fixture(actor, actor);
        when(attachments.existsByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(1L)).thenReturn(true);
        when(users.findByIdAndDeleteFlgFalse(200L)).thenReturn(Optional.of(
                UserLogin.builder().id(200L).role(Role.ADMIN).build()));
        byte[] original = {1, 2, 3};
        byte[] signed = {1, 2, 3, 4};
        when(pdfStorage.readPdf("storage://10")).thenReturn(original);
        when(signer.signBytes(eq(original), eq("100"), any(), any(), any(), any()))
                .thenReturn(new PdfSigner.SignResult("100", "author", "serial", "hash", new Date(), "sig_user_100", null, signed));
        when(pdfStorage.storeSignedPdf(signed, "initial_signed_1.pdf", 1L)).thenReturn("storage://11");
        when(documents.save(document)).thenReturn(document);

        service.submitForApproval(1L, request(), 100L);

        assertEquals(DocumentStatus.IN_REVIEW, document.getStatus());
        assertEquals("storage://11", document.getPrimaryFilePath());
        verify(signer).signBytes(eq(original), eq("100"), any(), any(), any(), any());
        verify(approvals).save(argThat(r -> r.getSenderUser().getId().equals(100L)));
    }

    @ParameterizedTest @EnumSource(Role.class)
    void workflowSubmitAlsoUsesDrafterForEveryRole(Role role) {
        WorkflowEngine engine = new WorkflowEngine(documents, mock(DocumentWorkflowLogRepository.class), mock(PermissionResolver.class));
        UserLogin actor = sender(role);
        Document document = Document.builder().drafter(actor).build();
        assertDoesNotThrow(() -> engine.validateActorPermission(document, actor, WorkflowAction.SUBMIT));
        document.setDrafter(UserLogin.builder().id(999L).build());
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION,
                assertThrows(BusinessException.class,
                        () -> engine.validateActorPermission(document, actor, WorkflowAction.SUBMIT)).getErrorCode());
        verifyNoInteractions(documents);
    }

    @Test
    void endpointUsesSessionIdAndDoesNotTrustAdminRoleInPrincipal() throws Exception {
        Document document = fixture(sender(Role.USER), UserLogin.builder().id(999L).build());
        DocumentController controller = new DocumentController(drafts, mock(DocumentAttachmentService.class),
                mock(DocumentVersionService.class), service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        var principal = new JwtUserPrincipal(100L, "author", "SYSTEM_ADMIN", true, List.of());
        mvc.perform(post("/documents/1/submit-approval")
                        .principal(new UsernamePasswordAuthenticationToken(principal, null, List.of()))
                        .contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(request())))
                .andExpect(status().isForbidden());
        verify(users).findByIdAndDeleteFlgFalse(100L);
        assertUnchangedAndNoSideEffects(document);
    }
}
