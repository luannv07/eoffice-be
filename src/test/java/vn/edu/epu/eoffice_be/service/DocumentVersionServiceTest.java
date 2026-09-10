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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private DocumentVersionService documentVersionService;

    @Test
    @DisplayName("Lấy danh sách các phiên bản (UC10)")
    void getVersions_Success() {
        Long docId = 1L;
        UserLogin creator = UserLogin.builder().id(100L).username("author").build();
        Document doc = Document.builder().id(docId).build();

        DocumentVersion v1 = DocumentVersion.builder().id(1L).document(doc).versionNumber(1).title("V1").summary("S1").createdByUser(creator).build();
        DocumentVersion v2 = DocumentVersion.builder().id(2L).document(doc).versionNumber(2).title("V2").summary("S2").createdByUser(creator).build();

        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentVersionRepository.findByDocumentIdAndDeleteFlgFalseOrderByVersionNumberDesc(docId))
                .thenReturn(List.of(v2, v1));

        List<DocumentVersionResponse> versions = documentVersionService.getVersions(docId);

        assertEquals(2, versions.size());
        assertEquals(2, versions.get(0).getVersionNumber());
        assertEquals(1, versions.get(1).getVersionNumber());
    }

    @Test
    @DisplayName("So sánh 2 phiên bản phát hiện thay đổi tiêu đề và trích yếu (UC10)")
    void compareVersions_Success() {
        Long docId = 1L;
        Document doc = Document.builder().id(docId).build();

        DocumentVersion v1 = DocumentVersion.builder().document(doc).versionNumber(1).title("Quyết định cũ").summary("Tóm tắt 1").content("ND").build();
        DocumentVersion v2 = DocumentVersion.builder().document(doc).versionNumber(2).title("Quyết định mới").summary("Tóm tắt 2").content("ND").build();

        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentVersionRepository.findByDocumentIdAndVersionNumberAndDeleteFlgFalse(docId, 1)).thenReturn(Optional.of(v1));
        when(documentVersionRepository.findByDocumentIdAndVersionNumberAndDeleteFlgFalse(docId, 2)).thenReturn(Optional.of(v2));

        VersionCompareResponse diff = documentVersionService.compareVersions(docId, 1, 2);

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

        UserLogin creator = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        Document doc = Document.builder()
                .id(docId)
                .title("Bản hiện tại ver 2")
                .summary("Trích yếu ver 2")
                .content("Nội dung ver 2")
                .creatorUser(creator)
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

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(creator));
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

        UserLogin creator = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        Document doc = Document.builder().id(docId).creatorUser(creator).status(DocumentStatus.DRAFT).currentVersion(2).build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(creator));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentVersionRepository.findByDocumentIdAndVersionNumberAndDeleteFlgFalse(docId, targetVer)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentVersionService.restoreVersion(docId, targetVer, userId));
        assertEquals(ErrorCode.DOCUMENT_VERSION_NOT_FOUND, ex.getErrorCode());
    }
}
