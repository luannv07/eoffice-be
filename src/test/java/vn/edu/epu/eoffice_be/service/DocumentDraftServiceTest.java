package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.epu.eoffice_be.common.enums.DocumentSecurityLevel;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.DocumentUrgencyLevel;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.DocumentCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.DocumentDetailResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentProcessLog;
import vn.edu.epu.eoffice_be.entity.DocumentVersion;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DictionaryItemRepository;
import vn.edu.epu.eoffice_be.repository.DocumentAttachmentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentProcessLogRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentVersionRepository;
import vn.edu.epu.eoffice_be.repository.OrgNodeRepository;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentDraftServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentVersionRepository documentVersionRepository;
    @Mock
    private DocumentAttachmentRepository documentAttachmentRepository;
    @Mock
    private DocumentProcessLogRepository documentProcessLogRepository;
    @Mock
    private OrgNodeRepository orgNodeRepository;
    @Mock
    private UserLoginRepository userLoginRepository;
    @Mock
    private UserInfoRepository userInfoRepository;
    @Mock
    private DictionaryItemRepository dictionaryItemRepository;

    @InjectMocks
    private DocumentDraftService documentDraftService;

    @Test
    @DisplayName("Tạo dự thảo thành công (UC08): sinh mã, gán DRAFT, tạo version 1")
    void createDraft_Success() {
        Long userId = 100L;
        Long orgNodeId = 10L;

        UserLogin creator = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        OrgNode orgNode = OrgNode.builder().id(orgNodeId).nameVn("Khoa CNTT").build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(creator));
        when(orgNodeRepository.findByIdAndDeleteFlgFalse(orgNodeId)).thenReturn(Optional.of(orgNode));
        when(documentRepository.existsByDraftCodeAndDeleteFlgFalse(any())).thenReturn(false);
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document doc = inv.getArgument(0);
            doc.setId(1L);
            return doc;
        });

        DocumentCreateRequest request = DocumentCreateRequest.builder()
                .title("Quyết định thực tập tốt nghiệp 2026")
                .summary("Quy định kế hoạch thực tập cho sinh viên")
                .content("Nội dung quyết định chi tiết")
                .documentTypeCd("QUYET_DINH")
                .issuingOrgNodeId(orgNodeId)
                .securityLevel(DocumentSecurityLevel.NORMAL)
                .urgencyLevel(DocumentUrgencyLevel.NORMAL)
                .build();

        DocumentResponse response = documentDraftService.createDraft(request, userId);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Quyết định thực tập tốt nghiệp 2026", response.getTitle());
        assertEquals(DocumentStatus.DRAFT, response.getStatus());
        assertEquals(1, response.getCurrentVersion());

        verify(documentVersionRepository).save(any(DocumentVersion.class));
        verify(documentProcessLogRepository).save(any(DocumentProcessLog.class));
    }

    @Test
    @DisplayName("Tạo dự thảo thất bại khi không tìm thấy đơn vị ban hành")
    void createDraft_OrgNodeNotFound_ShouldThrowBadRequest() {
        Long userId = 100L;
        Long orgNodeId = 999L;

        UserLogin creator = UserLogin.builder().id(userId).username("author").build();
        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(creator));
        when(orgNodeRepository.findByIdAndDeleteFlgFalse(orgNodeId)).thenReturn(Optional.empty());

        DocumentCreateRequest request = DocumentCreateRequest.builder()
                .title("Test")
                .summary("Summary")
                .documentTypeCd("CONG_VAN")
                .issuingOrgNodeId(orgNodeId)
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentDraftService.createDraft(request, userId));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("Chỉnh sửa dự thảo và tạo phiên bản mới (UC10)")
    void updateDraft_Success_WithNewVersion() {
        Long userId = 100L;
        Long docId = 1L;
        Long orgId = 10L;

        UserLogin creator = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        OrgNode orgNode = OrgNode.builder().id(orgId).nameVn("Phòng Đào tạo").build();

        Document doc = Document.builder()
                .id(docId)
                .draftCode("DT-2026-00001")
                .title("Tiêu đề cũ")
                .summary("Trích yếu cũ")
                .content("Nội dung cũ")
                .creatorUser(creator)
                .issuingOrgNode(orgNode)
                .status(DocumentStatus.DRAFT)
                .currentVersion(1)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(creator));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(orgNodeRepository.findByIdAndDeleteFlgFalse(orgId)).thenReturn(Optional.of(orgNode));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentUpdateRequest request = DocumentUpdateRequest.builder()
                .title("Tiêu đề mới")
                .summary("Trích yếu mới")
                .content("Nội dung mới")
                .documentTypeCd("QUYET_DINH")
                .issuingOrgNodeId(orgId)
                .changeSummary("Cập nhật theo yêu cầu lãnh đạo")
                .saveNewVersion(true)
                .build();

        DocumentResponse response = documentDraftService.updateDraft(docId, request, userId);

        assertEquals("Tiêu đề mới", response.getTitle());
        assertEquals(2, response.getCurrentVersion());
        verify(documentVersionRepository).save(any(DocumentVersion.class));
        verify(documentProcessLogRepository).save(any(DocumentProcessLog.class));
    }

    @Test
    @DisplayName("Chỉnh sửa dự thảo bị từ chối khi không phải người tạo (FORBIDDEN)")
    void updateDraft_Forbidden_WhenNotCreator() {
        Long creatorId = 100L;
        Long otherUserId = 200L;
        Long docId = 1L;

        UserLogin creator = UserLogin.builder().id(creatorId).username("author").build();
        UserLogin otherUser = UserLogin.builder().id(otherUserId).username("other").role(Role.USER).build();

        Document doc = Document.builder()
                .id(docId)
                .creatorUser(creator)
                .status(DocumentStatus.DRAFT)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(otherUserId)).thenReturn(Optional.of(otherUser));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));

        DocumentUpdateRequest request = DocumentUpdateRequest.builder()
                .title("Sửa lén")
                .summary("Summary")
                .issuingOrgNodeId(10L)
                .documentTypeCd("CONG_VAN")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentDraftService.updateDraft(docId, request, otherUserId));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Chỉnh sửa dự thảo bị từ chối khi văn bản đang chờ duyệt (DOCUMENT_INVALID_STATUS)")
    void updateDraft_InvalidStatus_WhenWaitingApproval() {
        Long userId = 100L;
        Long docId = 1L;

        UserLogin creator = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        Document doc = Document.builder()
                .id(docId)
                .creatorUser(creator)
                .status(DocumentStatus.WAITING_APPROVAL)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(creator));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));

        DocumentUpdateRequest request = DocumentUpdateRequest.builder()
                .title("Tiêu đề mới")
                .summary("Summary")
                .issuingOrgNodeId(10L)
                .documentTypeCd("CONG_VAN")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentDraftService.updateDraft(docId, request, userId));
        assertEquals(ErrorCode.DOCUMENT_INVALID_STATUS, ex.getErrorCode());
    }

    @Test
    @DisplayName("Lấy chi tiết dự thảo thành công (kèm tệp đính kèm và danh sách phiên bản)")
    void getDraftDetail_Success() {
        Long docId = 1L;
        Long userId = 100L;

        UserLogin creator = UserLogin.builder().id(userId).username("author").build();
        Document doc = Document.builder()
                .id(docId)
                .draftCode("DT-2026-00001")
                .title("Quyết định mẫu")
                .summary("Trích yếu mẫu")
                .creatorUser(creator)
                .status(DocumentStatus.DRAFT)
                .currentVersion(1)
                .build();

        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentAttachmentRepository.findByDocumentIdAndDeleteFlgFalse(docId)).thenReturn(Collections.emptyList());
        when(documentVersionRepository.findByDocumentIdAndDeleteFlgFalseOrderByVersionNumberDesc(docId)).thenReturn(Collections.emptyList());

        DocumentDetailResponse detail = documentDraftService.getDraftDetail(docId, userId);

        assertNotNull(detail);
        assertEquals("DT-2026-00001", detail.getDocument().getDraftCode());
    }
}
