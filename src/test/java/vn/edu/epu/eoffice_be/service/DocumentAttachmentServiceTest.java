package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.response.DocumentAttachmentResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentAttachment;
import vn.edu.epu.eoffice_be.entity.DocumentProcessLog;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DocumentApprovalRequestRepository;
import vn.edu.epu.eoffice_be.repository.DocumentAttachmentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentProcessLogRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentAttachmentServiceTest {

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

    @InjectMocks
    private DocumentAttachmentService documentAttachmentService;

    @Test
    @DisplayName("Tải tệp đính kèm thành công (UC09)")
    void uploadAttachment_Success() {
        Long userId = 100L;
        Long docId = 1L;

        UserLogin creator = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        Document doc = Document.builder().id(docId).creatorUser(creator).status(DocumentStatus.DRAFT).build();

        MockMultipartFile file = new MockMultipartFile("file", "kehoach.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "dummy content".getBytes());

        StoredFileInfo storedInfo = StoredFileInfo.builder()
                .originalFileName("kehoach.docx")
                .storageFileName("uuid-123.docx")
                .storagePath("uploads/documents/2026/09/1")
                .fileExtension("DOCX")
                .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .fileSizeBytes(100L)
                .sha256Checksum("hash123")
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(creator));
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
        assertEquals("kehoach.docx", response.getOriginalFileName());
        assertTrue(response.isMainDocument());

        verify(documentProcessLogRepository).save(any(DocumentProcessLog.class));
    }

    @Test
    @DisplayName("Tải tệp bị từ chối khi văn bản đã được phát hành (DOCUMENT_INVALID_STATUS)")
    void uploadAttachment_InvalidStatus_ShouldThrowInvalidStatus() {
        Long userId = 100L;
        Long docId = 1L;

        UserLogin creator = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        Document doc = Document.builder().id(docId).creatorUser(creator).status(DocumentStatus.ISSUED).build();

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "dummy".getBytes());

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(creator));
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

        UserLogin creator = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        Document doc = Document.builder().id(docId).creatorUser(creator).status(DocumentStatus.DRAFT).build();
        DocumentAttachment att = DocumentAttachment.builder()
                .id(attId)
                .document(doc)
                .originalFileName("old.pdf")
                .deleteFlg(false)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(creator));
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

        UserLogin creator = UserLogin.builder().id(userId).username("creator").role(Role.USER).build();
        Document doc = Document.builder().id(docId).creatorUser(creator).status(DocumentStatus.DRAFT).build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(creator));
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

        UserLogin creator = UserLogin.builder().id(userId).username("creator").role(Role.USER).build();
        Document doc = Document.builder().id(docId).creatorUser(creator).status(DocumentStatus.DRAFT).build();
        DocumentAttachment att = DocumentAttachment.builder()
                .id(attId)
                .document(doc)
                .storagePath("path/to")
                .storageFileName("file.pdf")
                .build();

        org.springframework.core.io.Resource mockResource = org.mockito.Mockito.mock(org.springframework.core.io.Resource.class);

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(creator));
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
        Long creatorId = 100L;

        UserLogin creator = UserLogin.builder().id(creatorId).username("creator").role(Role.USER).build();
        UserLogin crossUser = UserLogin.builder().id(crossUserId).username("crossUser").role(Role.USER).build();
        Document doc = Document.builder().id(docId).creatorUser(creator).status(DocumentStatus.DRAFT).build();

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

        UserLogin creator = UserLogin.builder().id(userId).username("creator").role(Role.USER).build();
        Document doc = Document.builder().id(docId).creatorUser(creator).status(DocumentStatus.DRAFT).build();
        DocumentAttachment att = DocumentAttachment.builder().id(attId).document(doc).originalFileName("test.docx").build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(creator));
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
        Long creatorId = 100L;

        UserLogin creator = UserLogin.builder().id(creatorId).username("creator").role(Role.USER).build();
        UserLogin crossUser = UserLogin.builder().id(crossUserId).username("crossUser").role(Role.USER).build();
        Document doc = Document.builder().id(docId).creatorUser(creator).status(DocumentStatus.DRAFT).build();

        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(crossUserId)).thenReturn(Optional.of(crossUser));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentAttachmentService.getAttachmentMetadata(docId, attId, crossUserId));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Lấy metadata tệp đính kèm thành công khi người dùng là Quản trị viên (ADMIN)")
    void getAttachmentMetadata_Admin_Success() {
        Long docId = 1L;
        Long attId = 10L;
        Long adminId = 999L;
        Long creatorId = 100L;

        UserLogin creator = UserLogin.builder().id(creatorId).username("creator").role(Role.USER).build();
        UserLogin admin = UserLogin.builder().id(adminId).username("admin").role(Role.ADMIN).build();
        Document doc = Document.builder().id(docId).creatorUser(creator).status(DocumentStatus.DRAFT).build();
        DocumentAttachment att = DocumentAttachment.builder().id(attId).document(doc).originalFileName("test.docx").build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(adminId)).thenReturn(Optional.of(admin));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentAttachmentRepository.findByIdAndDocumentIdAndDeleteFlgFalse(attId, docId)).thenReturn(Optional.of(att));

        DocumentAttachment result = documentAttachmentService.getAttachmentMetadata(docId, attId, adminId);
        assertNotNull(result);
        assertEquals("test.docx", result.getOriginalFileName());
    }

    @Test
    @DisplayName("Lấy metadata tệp đính kèm thành công với văn bản đã ban hành (ISSUED)")
    void getAttachmentMetadata_IssuedDocument_Success() {
        Long docId = 1L;
        Long attId = 10L;
        Long anyUserId = 500L;
        Long creatorId = 100L;

        UserLogin creator = UserLogin.builder().id(creatorId).username("creator").role(Role.USER).build();
        UserLogin anyUser = UserLogin.builder().id(anyUserId).username("employee").role(Role.USER).build();
        Document doc = Document.builder().id(docId).creatorUser(creator).status(DocumentStatus.ISSUED).build();
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
        UserLogin creator = UserLogin.builder().id(999L).username("creator").role(Role.USER).build();
        Document doc = Document.builder().id(docId).creatorUser(creator).status(DocumentStatus.DRAFT).build();

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
        UserLogin creator = UserLogin.builder().id(999L).username("creator").role(Role.USER).build();
        Document doc = Document.builder().id(docId).creatorUser(creator).status(DocumentStatus.DRAFT).build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(otherUser));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentAttachmentService.deleteAttachment(docId, attId, userId));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }
}
