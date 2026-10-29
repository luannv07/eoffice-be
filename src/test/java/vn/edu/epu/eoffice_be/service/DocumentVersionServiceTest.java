package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentVersionResponse;
import vn.edu.epu.eoffice_be.dto.response.VersionCompareResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentProcessLog;
import vn.edu.epu.eoffice_be.entity.DocumentVersion;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DocumentProcessLogRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentVersionRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentVersionServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentVersionRepository documentVersionRepository;
    @Mock
    private DocumentProcessLogRepository documentProcessLogRepository;
    @Mock
    private UserLoginRepository userLoginRepository;
    @Mock
    private DocumentDraftService documentDraftService;

    @org.mockito.Mock
    private vn.edu.epu.eoffice_be.service.DocumentReadPolicy draftReadPolicy;
    @org.mockito.Mock
    private vn.edu.epu.eoffice_be.service.DraftUnitResolver draftUnitResolver;
    @org.mockito.Mock
    private vn.edu.epu.eoffice_be.service.DocumentFileReadGuard documentFiles;

    @InjectMocks
    private DocumentVersionService documentVersionService;

    @org.junit.jupiter.api.BeforeEach
    void useActualReadPolicy() {
        org.springframework.test.util.ReflectionTestUtils.setField(documentVersionService, "readPolicy",
                new DocumentReadPolicy(userLoginRepository, org.mockito.Mockito.mock(vn.edu.epu.eoffice_be.repository.DocumentApprovalRequestRepository.class)));
    }

    @Test
    @DisplayName("Lấy danh sách các phiên bản với userId hợp lệ (UC10)")
    void getVersions_WithUserId_Success() {
        Long docId = 1L;
        Long userId = 100L;
        UserLogin drafter = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(drafter).build();

        DocumentVersion v1 = DocumentVersion.builder().id(1L).document(doc).versionNumber(1).title("V1").summary("S1").createdByUser(drafter).build();
        DocumentVersion v2 = DocumentVersion.builder().id(2L).document(doc).versionNumber(2).title("V2").summary("S2").createdByUser(drafter).build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(drafter));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentVersionRepository.findByDocumentIdAndDeleteFlgFalseOrderByVersionNumberDesc(docId))
                .thenReturn(List.of(v2, v1));

        List<DocumentVersionResponse> versions = documentVersionService.getVersions(docId, userId);

        assertEquals(2, versions.size());
        assertEquals(2, versions.get(0).getVersionNumber());
        assertEquals(1, versions.get(1).getVersionNumber());
    }

    @Test
    @DisplayName("Lấy danh sách các phiên bản thất bại khi không có quyền (UC10)")
    void getVersions_Forbidden() {
        Long docId = 1L;
        Long ownerId = 100L;
        Long otherUserId = 200L;
        UserLogin owner = UserLogin.builder().id(ownerId).username("author").role(Role.USER).build();
        UserLogin other = UserLogin.builder().id(otherUserId).username("other").role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(owner).build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(otherUserId)).thenReturn(Optional.of(other));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                documentVersionService.getVersions(docId, otherUserId));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Lấy danh sách các phiên bản thất bại khi userId null (UNAUTHORIZED)")
    void getVersions_NullUserId_ThrowsUnauthorized() {
        Long docId = 1L;
        Document doc = Document.builder().id(docId).build();
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                documentVersionService.getVersions(docId, null));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Lấy danh sách các phiên bản thất bại khi văn bản không tồn tại (DOCUMENT_NOT_FOUND)")
    void getVersions_DocumentNotFound_ThrowsNotFound() {
        Long docId = 1L;
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                documentVersionService.getVersions(docId, 100L));
        assertEquals(ErrorCode.DOCUMENT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("So sánh 2 phiên bản phát hiện thay đổi tiêu đề và trích yếu (UC10)")
    void compareVersions_Success() {
        Long docId = 1L;
        Long userId = 100L;
        UserLogin drafter = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(drafter).build();

        DocumentVersion v1 = DocumentVersion.builder().document(doc).versionNumber(1).title("Quyết định cũ").summary("Tóm tắt 1").content("ND").build();
        DocumentVersion v2 = DocumentVersion.builder().document(doc).versionNumber(2).title("Quyết định mới").summary("Tóm tắt 2").content("ND").build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(drafter));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentVersionRepository.findByDocumentIdAndVersionNumberAndDeleteFlgFalse(docId, 1)).thenReturn(Optional.of(v1));
        when(documentVersionRepository.findByDocumentIdAndVersionNumberAndDeleteFlgFalse(docId, 2)).thenReturn(Optional.of(v2));

        VersionCompareResponse diff = documentVersionService.compareVersions(docId, 1, 2, userId);

        assertNotNull(diff);
        assertTrue(diff.isTitleChanged());
        assertEquals("Quyết định cũ", diff.getOldTitle());
        assertEquals("Quyết định mới", diff.getNewTitle());
        assertTrue(diff.isSummaryChanged());
    }

    @Test
    @DisplayName("Khôi phục phiên bản cũ thành công và tăng current_version (UC10)")
    void restoreVersion_Success() {
        Long docId = 1L;
        Long userId = 100L;
        int targetVer = 1;

        UserLogin drafter = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        Document doc = Document.builder()
                .id(docId)
                .title("Bản hiện tại ver 2")
                .summary("Trích yếu ver 2")
                .content("Nội dung ver 2")
                .drafter(drafter)
                .status(DocumentStatus.DRAFT)
                .currentVersion(2)
                .build();

        DocumentVersion v1 = DocumentVersion.builder()
                .id(1L)
                .document(doc)
                .versionNumber(1)
                .title("Bản gốc ver 1")
                .summary("Trích yếu ver 1")
                .content("Nội dung ver 1")
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(drafter));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentVersionRepository.findByDocumentIdAndVersionNumberAndDeleteFlgFalse(docId, targetVer)).thenReturn(Optional.of(v1));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentDraftService.mapToDocumentResponse(any())).thenReturn(DocumentResponse.builder().id(docId).currentVersion(3).build());

        DocumentResponse response = documentVersionService.restoreVersion(docId, targetVer, userId);

        assertNotNull(response);
        assertEquals(3, doc.getCurrentVersion());
        assertEquals("Bản gốc ver 1", doc.getTitle());

        verify(documentVersionRepository).save(any(DocumentVersion.class));
        verify(documentProcessLogRepository).save(any(DocumentProcessLog.class));
    }

    @Test
    @DisplayName("Khôi phục phiên bản thất bại khi không tìm thấy phiên bản chỉ định (DOCUMENT_VERSION_NOT_FOUND)")
    void restoreVersion_VersionNotFound_ShouldThrowVersionNotFound() {
        Long docId = 1L;
        Long userId = 100L;
        int targetVer = 99;

        UserLogin drafter = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(drafter).status(DocumentStatus.DRAFT).currentVersion(2).build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(drafter));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentVersionRepository.findByDocumentIdAndVersionNumberAndDeleteFlgFalse(docId, targetVer)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentVersionService.restoreVersion(docId, targetVer, userId));
        assertEquals(ErrorCode.DOCUMENT_VERSION_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Khôi phục phiên bản thất bại khi người dùng không có quyền (FORBIDDEN)")
    void restoreVersion_Forbidden_ShouldThrowForbidden() {
        Long docId = 1L;
        Long userId = 200L;
        UserLogin otherUser = UserLogin.builder().id(userId).username("other").role(Role.USER).build();
        UserLogin drafter = UserLogin.builder().id(100L).username("drafter").role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(drafter).status(DocumentStatus.DRAFT).build();

        DocumentVersion v1 = DocumentVersion.builder().id(1L).document(doc).versionNumber(1).title("V1").build();
        lenient().when(documentVersionRepository.findByDocumentIdAndVersionNumberAndDeleteFlgFalse(docId, 1))
                .thenReturn(Optional.of(v1));

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(otherUser));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentVersionService.restoreVersion(docId, 1, userId));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Khôi phục phiên bản thất bại khi trạng thái văn bản không hợp lệ (DOCUMENT_INVALID_STATUS)")
    void restoreVersion_InvalidStatus_ShouldThrowInvalidStatus() {
        Long docId = 1L;
        Long userId = 100L;
        UserLogin drafter = UserLogin.builder().id(userId).username("drafter").role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(drafter).status(DocumentStatus.IN_REVIEW).build();

        DocumentVersion v1 = DocumentVersion.builder().id(1L).document(doc).versionNumber(1).title("V1").build();
        lenient().when(documentVersionRepository.findByDocumentIdAndVersionNumberAndDeleteFlgFalse(docId, 1))
                .thenReturn(Optional.of(v1));

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(drafter));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentVersionService.restoreVersion(docId, 1, userId));
        assertEquals(ErrorCode.DOCUMENT_INVALID_STATUS, ex.getErrorCode());
    }

    @Test
    @DisplayName("Admin không được khôi phục dự thảo do người khác soạn")
    void restoreVersion_UnrelatedAdmin_Forbidden() {
        Long docId=1L, adminId=999L;
        var admin=UserLogin.builder().id(adminId).role(Role.ADMIN).build();
        var doc=Document.builder().id(docId).drafter(UserLogin.builder().id(100L).build()).status(DocumentStatus.DRAFT).currentVersion(2).build();
        when(userLoginRepository.findByIdAndDeleteFlgFalse(adminId)).thenReturn(Optional.of(admin));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,
                () -> documentVersionService.restoreVersion(docId,1,adminId)).getErrorCode());
        assertEquals(2,doc.getCurrentVersion());
        org.mockito.Mockito.verifyNoInteractions(documentVersionRepository,documentProcessLogRepository,documentDraftService);
        org.mockito.Mockito.verify(documentRepository,org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("So sánh phiên bản thất bại khi userId null (UNAUTHORIZED)")
    void compareVersions_NullUserId_ThrowsUnauthorized() {
        Long docId = 1L;
        Document doc = Document.builder().id(docId).build();
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                documentVersionService.compareVersions(docId, 1, 2, null));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    @DisplayName("So sánh phiên bản thất bại khi văn bản không tồn tại (DOCUMENT_NOT_FOUND)")
    void compareVersions_DocumentNotFound_ThrowsNotFound() {
        Long docId = 1L;
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                documentVersionService.compareVersions(docId, 1, 2, 100L));
        assertEquals(ErrorCode.DOCUMENT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("So sánh phiên bản thất bại khi không phải người tạo và không phải admin (FORBIDDEN)")
    void compareVersions_Forbidden_ThrowsForbidden() {
        Long docId = 1L;
        Long ownerId = 100L;
        Long otherId = 200L;
        UserLogin other = UserLogin.builder().id(otherId).role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(UserLogin.builder().id(ownerId).build()).build();

        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(otherId)).thenReturn(Optional.of(other));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                documentVersionService.compareVersions(docId, 1, 2, otherId));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("So sánh 2 phiên bản thành công với userId hợp lệ")
    void compareVersions_Success_ValidUserId() {
        Long docId = 1L;
        Long userId = 100L;
        UserLogin drafter = UserLogin.builder().id(userId).username("drafter").role(Role.USER).build();
        Document doc = Document.builder().id(docId).drafter(drafter).build();

        DocumentVersion v1 = DocumentVersion.builder()
                .document(doc)
                .versionNumber(1)
                .title("Tiêu đề cũ")
                .summary("Trích yếu cũ")
                .content("Nội dung cũ")
                .build();
        DocumentVersion v2 = DocumentVersion.builder()
                .document(doc)
                .versionNumber(2)
                .title("Tiêu đề mới")
                .summary("Trích yếu cũ")
                .content("Nội dung mới")
                .build();

        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(drafter));
        when(documentVersionRepository.findByDocumentIdAndVersionNumberAndDeleteFlgFalse(docId, 1)).thenReturn(Optional.of(v1));
        when(documentVersionRepository.findByDocumentIdAndVersionNumberAndDeleteFlgFalse(docId, 2)).thenReturn(Optional.of(v2));

        VersionCompareResponse diff = documentVersionService.compareVersions(docId, 1, 2, userId);

        assertNotNull(diff);
        assertEquals(1, diff.getFromVersionNumber());
        assertEquals(2, diff.getToVersionNumber());
        assertTrue(diff.isTitleChanged());
        assertFalse(diff.isSummaryChanged());
        assertTrue(diff.isContentChanged());
        assertEquals("Tiêu đề cũ", diff.getOldTitle());
        assertEquals("Tiêu đề mới", diff.getNewTitle());
        assertEquals("Trích yếu cũ", diff.getOldSummary());
        assertEquals("Trích yếu cũ", diff.getNewSummary());
        assertEquals("Nội dung cũ", diff.getOldContent());
        assertEquals("Nội dung mới", diff.getNewContent());
    }

    @Test
    @DisplayName("ADMIN và SYSTEM_ADMIN không có ngoại lệ đọc phiên bản dự thảo")
    void assertCanViewDocumentVersions_UnrelatedAdmins_Forbidden() {
        var doc=Document.builder().id(1L).drafter(UserLogin.builder().id(100L).build()).status(DocumentStatus.DRAFT).build();
        for (Role role:List.of(Role.ADMIN,Role.SYSTEM_ADMIN)) {
            var actor=UserLogin.builder().id(999L).role(role).build();
            when(userLoginRepository.findByIdAndDeleteFlgFalse(999L)).thenReturn(Optional.of(actor));
            assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,
                    () -> documentVersionService.assertCanViewDocumentVersions(doc,999L)).getErrorCode());
        }
    }

    @Test
    @DisplayName("assertCanViewDocumentVersions thất bại khi không tìm thấy người dùng (UNAUTHORIZED)")
    void assertCanViewDocumentVersions_UserNotFound_ThrowsUnauthorized() {
        Long userId = 999L;
        Document doc = Document.builder().id(1L).drafter(UserLogin.builder().id(100L).build()).build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                documentVersionService.assertCanViewDocumentVersions(doc, userId));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }
}
