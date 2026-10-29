package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.epu.eoffice_be.config.UploadProperties;
import vn.edu.epu.eoffice_be.storage.FileSystemStorageService;
import vn.edu.epu.eoffice_be.storage.FileSecurityValidator;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.stream.Stream;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import vn.edu.epu.eoffice_be.common.enums.ApprovalRequestStatus;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.response.DocumentAttachmentResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentApprovalRequest;
import vn.edu.epu.eoffice_be.entity.DocumentAttachment;
import vn.edu.epu.eoffice_be.entity.DocumentProcessLog;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DocumentApprovalRequestRepository;
import vn.edu.epu.eoffice_be.repository.DocumentAttachmentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentProcessLogRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import org.springframework.core.io.Resource;
import vn.edu.epu.eoffice_be.repository.DocumentVersionRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.storage.FileStorageService;
import vn.edu.epu.eoffice_be.storage.dto.StoredFileInfo;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentAttachmentServiceTest {

    @org.junit.jupiter.api.BeforeEach
    void useActualReadPolicy() {
        org.springframework.test.util.ReflectionTestUtils.setField(documentAttachmentService, "readPolicy",
                new DocumentReadPolicy(userLoginRepository, documentApprovalRequestRepository));
    }

    @ParameterizedTest
    @MethodSource("nonPdfUploads")
    void uploadAttachment_rejectsNonPdfBeforeAnySideEffects(String filename, boolean isMain) {
        UserLogin drafter = UserLogin.builder().id(100L).role(Role.USER).build();
        Document document = Document.builder().id(1L).drafter(drafter).status(DocumentStatus.DRAFT).build();
        when(userLoginRepository.findByIdAndDeleteFlgFalse(100L)).thenReturn(Optional.of(drafter));
        when(documentRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(document));
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/pdf", "%PDF-1.4".getBytes());

        BusinessException error = assertThrows(BusinessException.class,
                () -> documentAttachmentService.uploadAttachment(1L, file, isMain, 100L));

        assertEquals(ErrorCode.FILE_INVALID_FORMAT, error.getErrorCode());
        assertEquals("Chỉ chấp nhận tệp PDF", error.getMessage());
        verifyNoInteractions(fileStorageService, documentAttachmentRepository,
                documentVersionRepository, documentProcessLogRepository);
        assertEquals(DocumentStatus.DRAFT, document.getStatus());
    }

    static Stream<Arguments> nonPdfUploads() {
        return Stream.of("file.docx", "file.doc", "file.png", "file", "vanban.pdf.docx", "file.")
                .flatMap(name -> Stream.of(true, false)
                        .map(main -> Arguments.of(name, main)));
    }

    @ParameterizedTest
    @MethodSource("pdfUploads")
    void uploadAttachment_acceptsPdfRegardlessOfMime(String filename, String mime) {
        UserLogin drafter = UserLogin.builder().id(100L).role(Role.USER).build();
        Document document = Document.builder().id(1L).drafter(drafter).status(DocumentStatus.DRAFT).build();
        when(userLoginRepository.findByIdAndDeleteFlgFalse(100L)).thenReturn(Optional.of(drafter));
        when(documentRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(document));
        MockMultipartFile file = new MockMultipartFile("file", filename, mime, "%PDF-1.4".getBytes());
        when(fileStorageService.storeFile(file, 1L)).thenReturn(StoredFileInfo.builder()
                .originalFileName(filename).fileExtension("PDF").build());
        when(documentAttachmentRepository.save(any(DocumentAttachment.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(filename, documentAttachmentService.uploadAttachment(1L, file, false, 100L).getOriginalFileName());
        verify(fileStorageService).storeFile(file, 1L);
        verify(documentProcessLogRepository).save(any(DocumentProcessLog.class));
    }

    static Stream<Arguments> pdfUploads() {
        return Stream.of("file.pdf", "file.PDF")
                .flatMap(name -> Stream.of("application/pdf", "application/octet-stream", null)
                        .map(mime -> Arguments.of(name, mime)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void uploadAttachment_realStorageRejectsFakePdfWithoutWriting(boolean isMain,
            @TempDir Path directory) throws Exception {
        assertRejectedByRealStorage(new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a PDF".getBytes()),
                ErrorCode.FILE_INVALID_FORMAT, isMain, directory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "empty", "nullName", "blankName"})
    void uploadAttachment_invalidInputKeepsExistingValidatorErrors(String input,
            @TempDir Path directory) throws Exception {
        MultipartFile file = switch (input) {
            case "null" -> null;
            case "empty" -> new MockMultipartFile("file", "file.docx", null, new byte[0]);
            case "nullName" -> new MockMultipartFile("file", null, null, "%PDF-1.4".getBytes());
            default -> new MockMultipartFile("file", " ", null, "%PDF-1.4".getBytes());
        };
        assertRejectedByRealStorage(file, ErrorCode.BAD_REQUEST, true, directory);
    }

    private void assertRejectedByRealStorage(MultipartFile file,
            ErrorCode expected, boolean isMain, Path directory) throws Exception {
        UserLogin drafter = UserLogin.builder().id(100L).role(Role.USER).build();
        when(userLoginRepository.findByIdAndDeleteFlgFalse(100L)).thenReturn(Optional.of(drafter));
        when(documentRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(
                Document.builder().id(1L).drafter(drafter).status(DocumentStatus.DRAFT).build()));
        var properties = new UploadProperties();
        properties.setDir(directory.toString());
        var storage = new FileSystemStorageService(properties,
                new FileSecurityValidator(properties));
        var service = new DocumentAttachmentService(documentRepository, org.mockito.Mockito.mock(vn.edu.epu.eoffice_be.service.DocumentReadPolicy.class), documentAttachmentRepository, documentVersionRepository, documentProcessLogRepository, userLoginRepository, storage, documentApprovalRequestRepository, null);

        assertEquals(expected, assertThrows(BusinessException.class,
                () -> service.uploadAttachment(1L, file, isMain, 100L)).getErrorCode());
        verifyNoInteractions(documentAttachmentRepository, documentVersionRepository,
                documentProcessLogRepository);
        try (var files = Files.list(directory)) {
            assertEquals(0L, files.count());
        }
    }

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentAttachmentRepository documentAttachmentRepository;
    @Mock
    private DocumentVersionRepository documentVersionRepository;
    @Mock
    private DocumentProcessLogRepository documentProcessLogRepository;
    @Mock
    private UserLoginRepository userLoginRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private DocumentApprovalRequestRepository documentApprovalRequestRepository;

    @org.mockito.Mock
    private vn.edu.epu.eoffice_be.service.DocumentReadPolicy draftReadPolicy;
    @org.mockito.Mock
    private vn.edu.epu.eoffice_be.service.DraftUnitResolver draftUnitResolver;
    @org.mockito.Mock
    private vn.edu.epu.eoffice_be.service.DocumentFileReadGuard documentFiles;

    @InjectMocks
    private DocumentAttachmentService documentAttachmentService;

    @Test
    @DisplayName("Tải tệp đính kèm thành công (UC09)")
    void uploadAttachment_Success() {
        Long userId = 100L;
        Long docId = 1L;

        UserLogin drafter = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(drafter).status(DocumentStatus.DRAFT).build();

        MockMultipartFile file = new MockMultipartFile("file", "kehoach.pdf",
                "application/pdf", "%PDF-1.4\n%%EOF".getBytes());

        StoredFileInfo storedInfo = StoredFileInfo.builder()
                .originalFileName("kehoach.pdf")
                .storageFileName("uuid-123.pdf")
                .storagePath("uploads/documents/2026/09/1")
                .fileExtension("PDF")
                .mimeType("application/pdf")
                .fileSizeBytes(100L)
                .sha256Checksum("hash123")
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(drafter));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(fileStorageService.storeFile(file, docId)).thenReturn(storedInfo);
        when(documentAttachmentRepository.save(any(DocumentAttachment.class))).thenAnswer(inv -> {
            DocumentAttachment att = inv.getArgument(0);
            att.setId(10L);
            return att;
        });

        DocumentAttachmentResponse response = documentAttachmentService.uploadAttachment(docId, file, true, userId);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("kehoach.pdf", response.getOriginalFileName());
        assertTrue(response.isMainDocument());

        verify(documentProcessLogRepository).save(any(DocumentProcessLog.class));
    }

    @Test
    @DisplayName("Tải tệp bị từ chối khi văn bản đã được phát hành (DOCUMENT_INVALID_STATUS)")
    void uploadAttachment_InvalidStatus_ShouldThrowInvalidStatus() {
        Long userId = 100L;
        Long docId = 1L;

        UserLogin drafter = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(drafter).status(DocumentStatus.PUBLISHED).build();

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "dummy".getBytes());

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(drafter));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentAttachmentService.uploadAttachment(docId, file, false, userId));
        assertEquals(ErrorCode.DOCUMENT_INVALID_STATUS, ex.getErrorCode());
    }

    @Test
    @DisplayName("Xóa tệp đính kèm thành công (xóa mềm delete_flg = true)")
    void deleteAttachment_Success() {
        Long userId = 100L;
        Long docId = 1L;
        Long attId = 10L;

        UserLogin drafter = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(drafter).status(DocumentStatus.DRAFT).build();
        DocumentAttachment att = DocumentAttachment.builder()
                .id(attId)
                .document(doc)
                .originalFileName("old.pdf")
                .deleteFlg(false)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(drafter));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentAttachmentRepository.findByIdAndDocumentIdAndDeleteFlgFalse(attId, docId))
                .thenReturn(Optional.of(att));

        documentAttachmentService.deleteAttachment(docId, attId, userId);

        assertTrue(att.isDeleteFlg());
        verify(documentAttachmentRepository).save(att);
        verify(documentProcessLogRepository).save(any(DocumentProcessLog.class));
    }

    @Test
    @DisplayName("Tải xuống thất bại khi không tìm thấy tệp đính kèm (DOCUMENT_ATTACHMENT_NOT_FOUND)")
    void downloadAttachment_NotFound_ShouldThrowAttachmentNotFound() {
        Long docId = 1L;
        Long attId = 999L;
        Long userId = 100L;

        UserLogin drafter = UserLogin.builder().id(userId).username("drafter").role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(drafter).status(DocumentStatus.DRAFT).build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(drafter));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentAttachmentRepository.findByIdAndDocumentIdAndDeleteFlgFalse(attId, docId))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentAttachmentService.downloadAttachment(docId, attId, userId));
        assertEquals(ErrorCode.DOCUMENT_ATTACHMENT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Tải xuống thành công khi tệp tồn tại và người dùng là người tạo")
    void downloadAttachment_Success() {
        Long docId = 1L;
        Long attId = 10L;
        Long userId = 100L;

        UserLogin drafter = UserLogin.builder().id(userId).username("drafter").role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(drafter).status(DocumentStatus.DRAFT).build();
        DocumentAttachment att = DocumentAttachment.builder()
                .id(attId)
                .document(doc)
                .storagePath("path/to")
                .storageFileName("file.pdf")
                .build();

        Resource mockResource = mock(Resource.class);

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(drafter));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentAttachmentRepository.findByIdAndDocumentIdAndDeleteFlgFalse(attId, docId)).thenReturn(Optional.of(att));
        when(fileStorageService.loadAsResource("path/to", "file.pdf")).thenReturn(mockResource);

        org.springframework.core.io.Resource result = documentAttachmentService.downloadAttachment(docId, attId, userId);
        assertNotNull(result);
        assertEquals(mockResource, result);
    }

    @Test
    @DisplayName("Tải tệp đính kèm bị từ chối khi người dùng khác truy cập chéo (FORBIDDEN)")
    void downloadAttachment_CrossUser_ThrowsForbidden() {
        Long docId = 1L;
        Long attId = 10L;
        Long crossUserId = 200L;
        Long drafterId = 100L;

        UserLogin drafter = UserLogin.builder().id(drafterId).username("drafter").role(Role.USER).build();
        UserLogin crossUser = UserLogin.builder().id(crossUserId).username("crossUser").role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(drafter).status(DocumentStatus.DRAFT).build();

        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(crossUserId)).thenReturn(Optional.of(crossUser));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentAttachmentService.downloadAttachment(docId, attId, crossUserId));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Lấy metadata tệp đính kèm thành công khi là người tạo")
    void getAttachmentMetadata_Success() {
        Long docId = 1L;
        Long attId = 10L;
        Long userId = 100L;

        UserLogin drafter = UserLogin.builder().id(userId).username("drafter").role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(drafter).status(DocumentStatus.DRAFT).build();
        DocumentAttachment att = DocumentAttachment.builder().id(attId).document(doc).originalFileName("test.docx").build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(drafter));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentAttachmentRepository.findByIdAndDocumentIdAndDeleteFlgFalse(attId, docId)).thenReturn(Optional.of(att));

        DocumentAttachment result = documentAttachmentService.getAttachmentMetadata(docId, attId, userId);
        assertNotNull(result);
        assertEquals("test.docx", result.getOriginalFileName());
    }

    @Test
    @DisplayName("Lấy metadata tệp đính kèm bị từ chối khi người dùng khác truy cập chéo (FORBIDDEN)")
    void getAttachmentMetadata_CrossUser_ThrowsForbidden() {
        Long docId = 1L;
        Long attId = 10L;
        Long crossUserId = 200L;
        Long drafterId = 100L;

        UserLogin drafter = UserLogin.builder().id(drafterId).username("drafter").role(Role.USER).build();
        UserLogin crossUser = UserLogin.builder().id(crossUserId).username("crossUser").role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(drafter).status(DocumentStatus.DRAFT).build();

        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(crossUserId)).thenReturn(Optional.of(crossUser));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentAttachmentService.getAttachmentMetadata(docId, attId, crossUserId));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Admin ngoài luồng được đọc metadata, không được sửa hồ sơ")
    void getAttachmentMetadata_UnrelatedAdmin_ReadOnlySuccess() {
        Long docId=1L, attId=10L, adminId=999L;
        var admin=UserLogin.builder().id(adminId).role(Role.ADMIN).build();
        var doc=Document.builder().id(docId).drafter(UserLogin.builder().id(100L).build()).status(DocumentStatus.DRAFT).build();
        when(userLoginRepository.findByIdAndDeleteFlgFalse(adminId)).thenReturn(Optional.of(admin));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        var attachment=DocumentAttachment.builder().id(attId).document(doc).originalFileName("draft.pdf").build();
        when(documentAttachmentRepository.findByIdAndDocumentIdAndDeleteFlgFalse(attId,docId)).thenReturn(Optional.of(attachment));
        assertEquals(attachment,documentAttachmentService.getAttachmentMetadata(docId,attId,adminId));
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,
                () -> documentAttachmentService.deleteAttachment(docId,attId,adminId)).getErrorCode());
        verify(documentAttachmentRepository,org.mockito.Mockito.never()).save(any());
        verifyNoInteractions(fileStorageService);
    }

    @Test
    @DisplayName("Lấy metadata tệp đính kèm thành công với văn bản đã ban hành (ISSUED)")
    void getAttachmentMetadata_IssuedDocument_Success() {
        Long docId = 1L;
        Long attId = 10L;
        Long anyUserId = 500L;
        Long drafterId = 100L;

        UserLogin drafter = UserLogin.builder().id(drafterId).username("drafter").role(Role.USER).build();
        UserLogin anyUser = UserLogin.builder().id(anyUserId).username("employee").role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(drafter).status(DocumentStatus.PUBLISHED).build();
        DocumentAttachment att = DocumentAttachment.builder().id(attId).document(doc).originalFileName("decision.pdf").build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(anyUserId)).thenReturn(Optional.of(anyUser));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentAttachmentRepository.findByIdAndDocumentIdAndDeleteFlgFalse(attId, docId)).thenReturn(Optional.of(att));

        DocumentAttachment result = documentAttachmentService.getAttachmentMetadata(docId, attId, anyUserId);
        assertNotNull(result);
        assertEquals("decision.pdf", result.getOriginalFileName());
    }

    @Test
    @DisplayName("Tải tệp bị từ chối khi không phải người tạo và không phải admin (FORBIDDEN)")
    void uploadAttachment_Forbidden_ShouldThrowForbidden() {
        Long userId = 100L;
        Long docId = 1L;

        UserLogin otherUser = UserLogin.builder().id(userId).username("other").role(Role.USER).build();
        UserLogin drafter = UserLogin.builder().id(999L).username("drafter").role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(drafter).status(DocumentStatus.DRAFT).build();

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "dummy".getBytes());

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(otherUser));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentAttachmentService.uploadAttachment(docId, file, false, userId));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Xóa tệp bị từ chối khi không phải người tạo và không phải admin (FORBIDDEN)")
    void deleteAttachment_Forbidden_ShouldThrowForbidden() {
        Long userId = 100L;
        Long docId = 1L;
        Long attId = 10L;

        UserLogin otherUser = UserLogin.builder().id(userId).username("other").role(Role.USER).build();
        UserLogin drafter = UserLogin.builder().id(999L).username("drafter").role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(drafter).status(DocumentStatus.DRAFT).build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(otherUser));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentAttachmentService.deleteAttachment(docId, attId, userId));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Lấy metadata tệp đính kèm thành công khi người dùng là người duyệt có yêu cầu PENDING")
    void getAttachmentMetadata_PendingApprover_Success() {
        Long docId = 1L;
        Long attId = 10L;
        Long approverId = 300L;
        Long drafterId = 100L;

        UserLogin drafter = UserLogin.builder().id(drafterId).username("drafter").role(Role.USER).build();
        UserLogin approver = UserLogin.builder().id(approverId).username("approver").role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(drafter).status(DocumentStatus.IN_REVIEW).build();
        DocumentAttachment att = DocumentAttachment.builder().id(attId).document(doc).originalFileName("draft.docx").build();

        DocumentApprovalRequest pendingReq = DocumentApprovalRequest.builder()
                .id(50L)
                .document(doc)
                .approverUser(approver)
                .status(ApprovalRequestStatus.PENDING)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(approverId)).thenReturn(Optional.of(approver));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentApprovalRequestRepository.existsParticipant(docId,approverId)).thenReturn(true);
        when(documentAttachmentRepository.findByIdAndDocumentIdAndDeleteFlgFalse(attId, docId)).thenReturn(Optional.of(att));

        DocumentAttachment result = documentAttachmentService.getAttachmentMetadata(docId, attId, approverId);
        assertNotNull(result);
        assertEquals("draft.docx", result.getOriginalFileName());
    }

    @Test
    @DisplayName("Người duyệt trong yêu cầu REJECTED chưa xóa vẫn được đọc metadata")
    void getAttachmentMetadata_RejectedApprover_ReadOnlySuccess() {
        Long docId = 1L;
        Long attId = 10L;
        Long formerApproverId = 300L;
        Long drafterId = 100L;

        UserLogin drafter = UserLogin.builder().id(drafterId).username("drafter").role(Role.USER).build();
        UserLogin formerApprover = UserLogin.builder().id(formerApproverId).username("formerApprover").role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(drafter).status(DocumentStatus.IN_REVIEW).build();

        DocumentApprovalRequest oldReq = DocumentApprovalRequest.builder()
                .id(50L)
                .document(doc)
                .approverUser(formerApprover)
                .status(ApprovalRequestStatus.REJECTED)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(formerApproverId)).thenReturn(Optional.of(formerApprover));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentApprovalRequestRepository.existsParticipant(docId,formerApproverId)).thenReturn(true);
        var attachment=DocumentAttachment.builder().id(attId).document(doc).originalFileName("historical.pdf").build();
        when(documentAttachmentRepository.findByIdAndDocumentIdAndDeleteFlgFalse(attId,docId)).thenReturn(Optional.of(attachment));
        var result=documentAttachmentService.getAttachmentMetadata(docId,attId,formerApproverId);
        assertEquals("historical.pdf",result.getOriginalFileName());
        assertEquals(ApprovalRequestStatus.REJECTED,oldReq.getStatus());
        verifyNoInteractions(fileStorageService);
        verify(documentAttachmentRepository,org.mockito.Mockito.never()).save(any());
    }
}
