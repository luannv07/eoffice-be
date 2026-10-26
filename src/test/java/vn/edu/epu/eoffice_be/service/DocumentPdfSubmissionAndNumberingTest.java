package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.*;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.security.pki.PdfSigner;
import vn.edu.epu.eoffice_be.service.impl.DocumentBookServiceImpl;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Submission and numbering must never create PDF signatures as a side effect. */
@ExtendWith(MockitoExtension.class)
class DocumentPdfSubmissionAndNumberingTest {
    @Mock DocumentRepository documents;
    @Mock DocumentAttachmentRepository attachments;
    @Mock DocumentApprovalRequestRepository approvals;
    @Mock DocumentProcessLogRepository processLogs;
    @Mock DocumentBookRepository books;
    @Mock UserLoginRepository users;
    @Mock UserInfoRepository profiles;
    @Mock PositionAssignmentRepository positions;
    @Mock PositionPermissionRepository permissions;
    @Mock ApplicationEventPublisher events;
    @Mock DocumentDraftService drafts;
    @Mock DocumentWorkingPdfService working;
    @Mock DocumentSignatureGuard guard;
    @Mock PdfSigner signer;
    @Mock DocumentPdfStorageService storage;
    @InjectMocks DocumentApprovalService approval;
    @InjectMocks DocumentBookServiceImpl numbering;

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private Document prepareSubmission() {
        UserLogin sender = UserLogin.builder().id(1L).username("author").role(Role.USER).build();
        UserLogin receiver = UserLogin.builder().id(2L).username("reviewer").role(Role.ADMIN).build();
        Document document = Document.builder().id(50L).title("Title").summary("Summary")
                .content("Content").creatorUser(sender).drafter(sender).status(DocumentStatus.DRAFT).build();
        when(users.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(sender));
        when(users.findByIdAndDeleteFlgFalse(2L)).thenReturn(Optional.of(receiver));
        when(documents.findByIdAndDeleteFlgFalse(50L)).thenReturn(Optional.of(document));
        when(attachments.existsByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(50L)).thenReturn(true);
        return document;
    }

    @Test
    void submissionChecksExistingInitialsAndOnlyChangesStatus() {
        Document document = prepareSubmission();
        byte[] pdf = {1, 2};
        when(working.read(document)).thenReturn(pdf);
        when(documents.save(document)).thenReturn(document);
        approval.submitForApproval(50L, SubmitApprovalRequest.builder().approverUserId(2L).build(), 1L);
        verify(guard).requireInitial(document, document.getCreatorUser(), InitialSignStage.DRAFTER, pdf);
        assertEquals(DocumentStatus.IN_REVIEW, document.getStatus());
        verifyNoInteractions(signer, storage);
        verify(approvals).save(any());
        verify(events).publishEvent(any(vn.edu.epu.eoffice_be.event.DocumentSubmittedEvent.class));
    }

    @Test
    void unsignedSubmissionDoesNotChangeStatusOrCreateApproval() {
        Document document = prepareSubmission();
        doThrow(new BusinessException(ErrorCode.BAD_REQUEST)).when(guard).requireInitial(any(), any(), any(), any());
        assertThrows(BusinessException.class, () -> approval.submitForApproval(50L,
                SubmitApprovalRequest.builder().approverUserId(2L).build(), 1L));
        assertEquals(DocumentStatus.DRAFT, document.getStatus());
        verifyNoInteractions(approvals, events, signer, storage);
        verify(documents, never()).save(any());
    }

    @Test
    void numberingDoesNotReadOrSignPdfAndPreservesLeaderAndApprovedState() {
        UserLogin clerk = UserLogin.builder().id(3L).username("clerk").role(Role.CLERK).build();
        UserLogin rector = UserLogin.builder().id(2L).role(Role.LEADER).build();
        Document document = Document.builder().id(50L).status(DocumentStatus.APPROVED).signer(rector).build();
        DocumentBook book = DocumentBook.builder().id(10L).numberPattern("{NUM}/CV-TEST").currentNumber(0).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new JwtUserPrincipal(3L, "clerk", "CLERK", true, List.of()), null, List.of()));
        when(users.findByIdAndDeleteFlgFalse(3L)).thenReturn(Optional.of(clerk));
        when(books.findByIdForUpdate(10L)).thenReturn(Optional.of(book));
        when(documents.findById(50L)).thenReturn(Optional.of(document));
        AssignDocumentNumberRequest request = new AssignDocumentNumberRequest();
        request.setBookId(10L);
        request.setDocumentId(50L);
        var response = numbering.assignNumber(request);
        assertEquals("1/CV-TEST", response.getDocumentNumber());
        assertEquals(DocumentStatus.APPROVED, document.getStatus());
        assertSame(rector, document.getSigner());
        assertSame(book, document.getDocumentBook());
        verifyNoInteractions(signer, storage, working, guard);
    }
}
