package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.config.UploadProperties;
import vn.edu.epu.eoffice_be.dto.request.AssignDocumentNumberRequest;
import vn.edu.epu.eoffice_be.dto.request.SubmitApprovalRequest;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentBook;
import vn.edu.epu.eoffice_be.entity.StoredFileMetadata;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DocumentApprovalRequestRepository;
import vn.edu.epu.eoffice_be.repository.DocumentAttachmentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentBookRepository;
import vn.edu.epu.eoffice_be.repository.DocumentProcessLogRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentWorkflowLogRepository;
import vn.edu.epu.eoffice_be.repository.PositionAssignmentRepository;
import vn.edu.epu.eoffice_be.repository.PositionPermissionRepository;
import vn.edu.epu.eoffice_be.repository.StoredFileMetadataRepository;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.security.pki.PdfSigner;
import vn.edu.epu.eoffice_be.service.impl.DocumentBookServiceImpl;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageProvider;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageProviderRegistry;
import vn.edu.epu.eoffice_be.storage.provider.StoredFile;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Covers the two additional signing entry points without loading the app, a DB, or Telegram. */
@ExtendWith(MockitoExtension.class)
class DocumentPdfSubmissionAndNumberingTest {
    @Mock SubmissionRoutingService submissionRoutingService;
    @Mock vn.edu.epu.eoffice_be.workflow.WorkflowEngine submissionWorkflowEngine;

    enum Step { SUBMIT, ASSIGN_NUMBER }

    @Mock DocumentRepository documents;
    @Mock DocumentAttachmentRepository attachments;
    @Mock DocumentApprovalRequestRepository approvals;
    @Mock DocumentProcessLogRepository processLogs;
    @Mock DocumentWorkflowLogRepository workflowLogs;
    @Mock DocumentBookRepository books;
    @Mock UserLoginRepository users;
    @Mock UserInfoRepository userInfo;
    @Mock PositionAssignmentRepository positions;
    @Mock PositionPermissionRepository permissions;
    @Mock ApplicationEventPublisher events;
    @Mock DocumentDraftService drafts;
    @Mock vn.edu.epu.eoffice_be.storage.FileStorageService unifiedStorage;
    @Mock DocumentPdfStorageService storage;
    @Mock PdfSigner signer;
    @org.mockito.Mock
    private vn.edu.epu.eoffice_be.service.DocumentReadPolicy draftReadPolicy;
    @org.mockito.Mock
    private vn.edu.epu.eoffice_be.service.DraftUnitResolver draftUnitResolver;
    @org.mockito.Mock
    private vn.edu.epu.eoffice_be.service.DocumentFileReadGuard documentFiles;

    @InjectMocks DocumentApprovalService submission;
    @InjectMocks DocumentBookServiceImpl numbering;

    private static final byte[] PREVIOUS = {1, 2, 3};
    private static final byte[] SIGNED = {1, 2, 3, 4};

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @EnumSource(Step.class)
    void missingSignedVersionDoesNotFallBackToAttachment(Step step) {
        Document document = prepare(step);
        BusinessException failure = new BusinessException(ErrorCode.DOCUMENT_PDF_NOT_FOUND);
        when(storage.readPdf("storage://35001")).thenThrow(failure);

        assertSame(failure, assertThrows(BusinessException.class, () -> invoke(step, submission, numbering)));

        assertEquals("storage://35001", document.getPrimaryFilePath());
        verifyNoInteractions(signer, unifiedStorage, approvals, processLogs, workflowLogs, events);
        verify(attachments, never()).findByDocumentIdAndDeleteFlgFalse(anyLong());
        verify(storage, never()).storeSignedPdf(any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(Step.class)
    void uploadFailureKeepsOldVersionAndDoesNotRecordSigningSuccess(Step step) {
        Document document = prepare(step);
        prepareSigning(step);
        BusinessException failure = new BusinessException(ErrorCode.DOCUMENT_PDF_STORAGE_UNAVAILABLE);
        when(storage.storeSignedPdf(eq(SIGNED), eq("initial_signed_5001.pdf"), eq(5001L))).thenThrow(failure);

        assertSame(failure, assertThrows(BusinessException.class, () -> invoke(step, submission, numbering)));

        assertEquals("storage://35001", document.getPrimaryFilePath());
        assertEquals(step == Step.SUBMIT ? DocumentStatus.DRAFT : DocumentStatus.APPROVED, document.getStatus());
        verifyNoInteractions(approvals, processLogs, workflowLogs, events);
    }

    @ParameterizedTest
    @EnumSource(Step.class)
    void savesExactSignedBytesAndUpdatesReference(Step step) {
        Document document = prepare(step);
        prepareSigning(step);
        when(storage.storeSignedPdf(eq(SIGNED), eq("initial_signed_5001.pdf"), eq(5001L)))
                .thenReturn("storage://35002");
        if (step == Step.SUBMIT) when(documents.save(document)).thenReturn(document);

        invoke(step, submission, numbering);

        assertEquals("storage://35002", document.getPrimaryFilePath());
        verify(storage).readPdf("storage://35001");
        verify(storage).storeSignedPdf(eq(SIGNED), eq("initial_signed_5001.pdf"), eq(5001L));
        verifyNoInteractions(unifiedStorage);
        if (step == Step.SUBMIT) verify(approvals).save(any());
        else verify(workflowLogs).save(any());
    }

    @ParameterizedTest
    @EnumSource(Step.class)
    void laterDatabaseFailureRollsBackAndDiscardsOnlyNewUpload(Step step) {
        prepare(step);
        FileStorageService legacy = mock(FileStorageService.class);
        StoredFileMetadataRepository metadata = mock(StoredFileMetadataRepository.class);
        FileStorageProvider provider = mock(FileStorageProvider.class);
        when(provider.getName()).thenReturn("telegram");
        when(metadata.findById(35001L)).thenReturn(Optional.of(StoredFileMetadata.builder()
                .id(35001L).provider("telegram").fileId("old-file").build()));
        when(provider.get("old-file")).thenReturn(StoredFile.builder().content(PREVIOUS).build());
        when(provider.saveForDocument(any())).thenReturn(StoredFile.builder().id("new-file")
                .metadata(Map.of("messageId", "87", "chatId", "test-channel")).build());
        when(metadata.saveAndFlush(any())).thenAnswer(call -> {
            StoredFileMetadata saved = call.getArgument(0);
            saved.setId(35002L);
            return saved;
        });
        DocumentPdfStorageService realStorage = new DocumentPdfStorageService(
                new FileStorageProviderRegistry(List.of(provider), "telegram"), metadata, legacy, new UploadProperties());
        ReflectionTestUtils.setField(step == Step.SUBMIT ? submission : numbering, "documentPdfStorageService", realStorage);
        prepareSigner(step);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Test DB unavailable");
        when(documents.save(any())).thenThrow(failure);

        Object proxy = transactionalProxy(step == Step.SUBMIT ? submission : numbering);
        RuntimeException actual = assertThrows(RuntimeException.class, () -> {
            if (step == Step.SUBMIT) invoke(step, (DocumentApprovalService) proxy, numbering);
            else invoke(step, submission, (DocumentBookService) proxy);
        });
        if (step == Step.SUBMIT) assertSame(failure, actual);
        else assertEquals(ErrorCode.SIGNATURE_PROCESS_ERROR, assertInstanceOf(BusinessException.class, actual).getErrorCode());

        verify(provider).discardUpload(argThat(receipt -> "new-file".equals(receipt.getId())
                && "87".equals(receipt.getMetadata().get("messageId"))));
        verify(provider, never()).delete(anyString());
        verifyNoInteractions(legacy, approvals, processLogs, workflowLogs, events);
    }

    private Document prepare(Step step) {
        UserLogin actor = UserLogin.builder().id(100L).username("tester").role(Role.CLERK).build();
        when(users.findByIdAndDeleteFlgFalse(100L)).thenReturn(Optional.of(actor));
        Document document = Document.builder().id(5001L).title("Test").summary("Summary")
                .drafter(actor).primaryFilePath("storage://35001")
                .status(step == Step.SUBMIT ? DocumentStatus.DRAFT : DocumentStatus.APPROVED).build();
        if (step == Step.SUBMIT) {
            when(submissionRoutingService.resolve(any(), any(), any(), any())).thenReturn(
                    new SubmissionRoutingService.Decision(vn.edu.epu.eoffice_be.common.enums.SubmissionRoute.LEADER_APPROVAL, 200L));
            org.mockito.Mockito.lenient().when(submissionWorkflowEngine.executeTransition(any(), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(inv -> { document.setStatus(DocumentStatus.PENDING_APPROVAL); documents.save(document); return null; });
            when(documents.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
            when(users.findByIdAndDeleteFlgFalse(200L)).thenReturn(Optional.of(
                    UserLogin.builder().id(200L).username("approver").role(Role.ADMIN).build()));
            when(attachments.existsByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(5001L)).thenReturn(true);
        } else {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    new JwtUserPrincipal(100L, "tester", "CLERK", true, List.of()), null, List.of()));
            when(documents.findById(5001L)).thenReturn(Optional.of(document));
            when(books.findByIdForUpdate(1L)).thenReturn(Optional.of(DocumentBook.builder().id(1L)
                    .currentNumber(0).numberPattern("{NUM}/TEST").isClosed(false).build()));
        }
        return document;
    }

    private void prepareSigning(Step step) {
        when(storage.readPdf("storage://35001")).thenReturn(PREVIOUS);
        prepareSigner(step);
    }

    private void prepareSigner(Step step) {
        PdfSigner.SignResult result = new PdfSigner.SignResult("100", "tester", "serial", "signed-hash",
                new Date(), "field", null, SIGNED);
        if (step == Step.SUBMIT) when(signer.signBytes(eq(PREVIOUS), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(result);
        else when(signer.signBytes(eq(PREVIOUS), anyString(), anyString(), anyString())).thenReturn(result);
    }

    private void invoke(Step step, DocumentApprovalService submitService, DocumentBookService bookService) {
        if (step == Step.SUBMIT) submitService.submitForApproval(5001L,
                SubmitApprovalRequest.builder().approverUserId(200L).build(), 100L);
        else {
            AssignDocumentNumberRequest request = new AssignDocumentNumberRequest();
            request.setBookId(1L);
            request.setDocumentId(5001L);
            bookService.assignNumber(request);
        }
    }

    private Object transactionalProxy(Object target) {
        // Exercise the actual service transaction annotations; no external database is needed.
        AbstractPlatformTransactionManager manager = new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object tx, TransactionDefinition definition) { }
            @Override protected void doCommit(DefaultTransactionStatus status) { }
            @Override protected void doRollback(DefaultTransactionStatus status) { }
        };
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return factory.getProxy();
    }
}
