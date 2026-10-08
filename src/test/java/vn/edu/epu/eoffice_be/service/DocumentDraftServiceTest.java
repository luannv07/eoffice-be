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
import vn.edu.epu.eoffice_be.common.enums.DocumentProcessAction;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentAttachment;
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

import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
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
            ReflectionTestUtils.setField(doc, "id", 1L);
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
    @DisplayName("Chỉnh sửa dự thảo không làm hạ cấp độ mật/độ khẩn khi request có giá trị null")
    void updateDraft_NullSecurityLevel_PreservesExistingSecurityLevel() {
        Long userId = 100L;
        Long docId = 1L;
        Long orgId = 10L;

        UserLogin creator = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        OrgNode orgNode = OrgNode.builder().id(orgId).nameVn("Phòng Đào tạo").build();

        Document doc = Document.builder()
                .id(docId)
                .title("Tiêu đề mật")
                .summary("Trích yếu")
                .content("Nội dung")
                .securityLevel(DocumentSecurityLevel.SECRET)
                .urgencyLevel(DocumentUrgencyLevel.TOP_URGENT)
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
                .title("Tiêu đề cập nhật")
                .summary("Trích yếu")
                .content("Nội dung")
                .documentTypeCd("QUYET_DINH")
                .issuingOrgNodeId(orgId)
                .securityLevel(null) // Không gửi độ mật
                .urgencyLevel(null)  // Không gửi độ khẩn
                .saveNewVersion(false)
                .build();

        DocumentResponse response = documentDraftService.updateDraft(docId, request, userId);

        assertEquals(DocumentSecurityLevel.SECRET, response.getSecurityLevel());
        assertEquals(DocumentUrgencyLevel.TOP_URGENT, response.getUrgencyLevel());
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

    @Test
    @DisplayName("Lấy chi tiết dự thảo thành công khi creatorUser null (fallback sang drafter)")
    void getDraftDetail_WhenCreatorUserIsNull_FallbackToDrafter_Success() {
        Long docId = 2L;
        Long userId = 2001L;

        UserLogin drafter = UserLogin.builder().id(userId).username("drafterUser").build();
        Document doc = Document.builder()
                .id(docId)
                .draftCode("DT-2026-00002")
                .title("Quyết định ban hành")
                .summary("Trích yếu")
                .drafter(drafter)
                .creatorUser(null)
                .status(DocumentStatus.SUBMITTED)
                .currentVersion(1)
                .build();

        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentAttachmentRepository.findByDocumentIdAndDeleteFlgFalse(docId)).thenReturn(Collections.emptyList());
        when(documentVersionRepository.findByDocumentIdAndDeleteFlgFalseOrderByVersionNumberDesc(docId)).thenReturn(Collections.emptyList());

        DocumentDetailResponse detail = documentDraftService.getDraftDetail(docId, userId);

        assertNotNull(detail);
        assertEquals("DT-2026-00002", detail.getDocument().getDraftCode());
        assertEquals(userId, detail.getDocument().getCreatorUserId());
        assertEquals("drafterUser", detail.getDocument().getCreatorUsername());
    }

    @Test
    @DisplayName("Cập nhật dự thảo không tạo phiên bản mới (saveNewVersion = false)")
    void updateDraft_WithoutNewVersion() {
        Long userId = 100L;
        Long docId = 1L;
        Long orgNodeId = 10L;

        UserLogin creator = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        OrgNode orgNode = OrgNode.builder().id(orgNodeId).nameVn("Khoa CNTT").build();
        Document doc = Document.builder()
                .id(docId)
                .title("Tiêu đề cũ")
                .summary("Trích yếu cũ")
                .content("Nội dung cũ")
                .creatorUser(creator)
                .status(DocumentStatus.DRAFT)
                .currentVersion(1)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(creator));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(orgNodeRepository.findByIdAndDeleteFlgFalse(orgNodeId)).thenReturn(Optional.of(orgNode));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentUpdateRequest request = DocumentUpdateRequest.builder()
                .title("Tiêu đề cập nhật nhẹ")
                .summary("Trích yếu cập nhật")
                .content("Nội dung mới")
                .issuingOrgNodeId(orgNodeId)
                .documentTypeCd("CONG_VAN")
                .saveNewVersion(false)
                .build();

        DocumentResponse response = documentDraftService.updateDraft(docId, request, userId);

        assertNotNull(response);
        assertEquals("Tiêu đề cập nhật nhẹ", response.getTitle());
        assertEquals(1, doc.getCurrentVersion()); // Version không tăng
    }

    @Test
    @DisplayName("Admin có thể cập nhật dự thảo của người khác")
    void updateDraft_Admin_Success() {
        Long adminId = 999L;
        Long docId = 1L;
        Long orgNodeId = 10L;

        UserLogin admin = UserLogin.builder().id(adminId).username("admin").role(Role.ADMIN).build();
        UserLogin creator = UserLogin.builder().id(100L).username("author").role(Role.USER).build();
        OrgNode orgNode = OrgNode.builder().id(orgNodeId).nameVn("Khoa CNTT").build();
        Document doc = Document.builder()
                .id(docId)
                .creatorUser(creator)
                .status(DocumentStatus.DRAFT)
                .currentVersion(1)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(adminId)).thenReturn(Optional.of(admin));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(orgNodeRepository.findByIdAndDeleteFlgFalse(orgNodeId)).thenReturn(Optional.of(orgNode));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentUpdateRequest request = DocumentUpdateRequest.builder()
                .title("Admin sửa")
                .summary("Summary")
                .issuingOrgNodeId(orgNodeId)
                .documentTypeCd("CONG_VAN")
                .saveNewVersion(false)
                .build();

        DocumentResponse response = documentDraftService.updateDraft(docId, request, adminId);
        assertNotNull(response);
        assertEquals("Admin sửa", response.getTitle());
    }

    @Test
    @DisplayName("Tìm kiếm dự thảo với từ khóa có ký tự đặc biệt (searchDrafts)")
    void searchDrafts_WithSpecialCharacters() {
        Long userId = 100L;
        vn.edu.epu.eoffice_be.dto.request.DocumentFilterRequest filter =
                new vn.edu.epu.eoffice_be.dto.request.DocumentFilterRequest();
        filter.setKeyword("test%_\\");
        filter.setStatus(DocumentStatus.DRAFT);
        filter.setPage(0);
        filter.setSize(10);

        Document doc = Document.builder()
                .id(1L)
                .draftCode("DT-2026-00001")
                .title("Tài liệu test%_\\")
                .creatorUser(UserLogin.builder().id(userId).username("author").build())
                .status(DocumentStatus.DRAFT)
                .build();

        org.springframework.data.domain.Page<Document> mockPage =
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(doc));

        when(documentRepository.searchDocuments(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq(DocumentStatus.DRAFT),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("test\\%\\_\\\\"),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(mockPage);

        var result = documentDraftService.searchDrafts(filter, userId);
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Tìm kiếm dự thảo với từ khóa rỗng được chuẩn hóa về null")
    void searchDrafts_WithEmptyKeyword() {
        Long userId = 100L;
        vn.edu.epu.eoffice_be.dto.request.DocumentFilterRequest filter =
                new vn.edu.epu.eoffice_be.dto.request.DocumentFilterRequest();
        filter.setKeyword("   ");
        filter.setPage(0);
        filter.setSize(10);

        org.springframework.data.domain.Page<Document> mockPage =
                new org.springframework.data.domain.PageImpl<>(java.util.List.of());

        when(documentRepository.searchDocuments(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(mockPage);

        var result = documentDraftService.searchDrafts(filter, userId);
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
    }

    @Test
    @DisplayName("Tìm kiếm dự thảo mặc định sắp xếp giảm dần theo createdDate (createdDate DESC)")
    void searchDrafts_DefaultSortOrder_CreatedDateDesc() {
        Long userId = 100L;
        vn.edu.epu.eoffice_be.dto.request.DocumentFilterRequest filter =
                new vn.edu.epu.eoffice_be.dto.request.DocumentFilterRequest();
        filter.setPage(0);
        filter.setSize(20);

        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> pageableCaptor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);

        when(documentRepository.searchDocuments(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                pageableCaptor.capture()
        )).thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));

        documentDraftService.searchDrafts(filter, userId);

        org.springframework.data.domain.Pageable pageable = pageableCaptor.getValue();
        assertNotNull(pageable);
        assertEquals(0, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
        org.springframework.data.domain.Sort.Order order = pageable.getSort().getOrderFor("createdDate");
        assertNotNull(order);
        assertEquals(org.springframework.data.domain.Sort.Direction.DESC, order.getDirection());
    }

    @Test
    @DisplayName("Xóa dự thảo thành công bởi người tạo (Creator)")
    void deleteDraft_Success_ByCreator() {
        Long userId = 100L;
        Long docId = 1L;

        UserLogin creator = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        Document doc = Document.builder()
                .id(docId)
                .draftCode("DT-2026-00001")
                .title("Dự thảo kế hoạch")
                .creatorUser(creator)
                .status(DocumentStatus.DRAFT)
                .deleteFlg(false)
                .build();

        DocumentAttachment attachment = DocumentAttachment.builder()
                .id(10L)
                .document(doc)
                .originalFileName("test.pdf")
                .deleteFlg(false)
                .build();

        DocumentVersion version = DocumentVersion.builder()
                .id(20L)
                .document(doc)
                .versionNumber(1)
                .deleteFlg(false)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(creator));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentAttachmentRepository.findByDocumentIdAndDeleteFlgFalse(docId)).thenReturn(List.of(attachment));
        when(documentVersionRepository.findByDocumentIdAndDeleteFlgFalseOrderByVersionNumberDesc(docId)).thenReturn(List.of(version));

        documentDraftService.deleteDraft(docId, userId);

        assertTrue(doc.isDeleteFlg());
        assertTrue(attachment.isDeleteFlg());
        assertTrue(version.isDeleteFlg());

        verify(documentRepository).save(doc);
        verify(documentAttachmentRepository).saveAll(List.of(attachment));
        verify(documentVersionRepository).saveAll(List.of(version));
        verify(documentProcessLogRepository).save(any(DocumentProcessLog.class));
    }

    @Test
    @DisplayName("Xóa dự thảo thành công bởi quản trị viên (Admin)")
    void deleteDraft_Success_ByAdmin() {
        Long creatorId = 100L;
        Long adminId = 999L;
        Long docId = 1L;

        UserLogin creator = UserLogin.builder().id(creatorId).username("author").role(Role.USER).build();
        UserLogin admin = UserLogin.builder().id(adminId).username("admin").role(Role.ADMIN).build();
        Document doc = Document.builder()
                .id(docId)
                .draftCode("DT-2026-00001")
                .title("Dự thảo kế hoạch")
                .creatorUser(creator)
                .status(DocumentStatus.DRAFT)
                .deleteFlg(false)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(adminId)).thenReturn(Optional.of(admin));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));
        when(documentAttachmentRepository.findByDocumentIdAndDeleteFlgFalse(docId)).thenReturn(List.of());
        when(documentVersionRepository.findByDocumentIdAndDeleteFlgFalseOrderByVersionNumberDesc(docId)).thenReturn(List.of());

        documentDraftService.deleteDraft(docId, adminId);

        assertTrue(doc.isDeleteFlg());
        verify(documentRepository).save(doc);
        verify(documentProcessLogRepository).save(any(DocumentProcessLog.class));
    }

    @Test
    @DisplayName("Xóa dự thảo thất bại khi người dùng không tồn tại")
    void deleteDraft_ThrowsUnauthorized_WhenUserNotFound() {
        Long userId = 100L;
        Long docId = 1L;

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentDraftService.deleteDraft(docId, userId));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Xóa dự thảo thất bại khi không tìm thấy văn bản")
    void deleteDraft_ThrowsNotFound_WhenDocumentNotFound() {
        Long userId = 100L;
        Long docId = 1L;

        UserLogin user = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(user));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentDraftService.deleteDraft(docId, userId));
        assertEquals(ErrorCode.DOCUMENT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Xóa dự thảo thất bại khi người thực hiện không phải người tạo và không phải Admin")
    void deleteDraft_ThrowsForbidden_WhenUserNotCreatorAndNotAdmin() {
        Long creatorId = 100L;
        Long otherUserId = 200L;
        Long docId = 1L;

        UserLogin creator = UserLogin.builder().id(creatorId).username("author").role(Role.USER).build();
        UserLogin otherUser = UserLogin.builder().id(otherUserId).username("other").role(Role.USER).build();
        Document doc = Document.builder()
                .id(docId)
                .draftCode("DT-2026-00001")
                .creatorUser(creator)
                .status(DocumentStatus.DRAFT)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(otherUserId)).thenReturn(Optional.of(otherUser));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentDraftService.deleteDraft(docId, otherUserId));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Xóa dự thảo thất bại khi văn bản không ở trạng thái DRAFT")
    void deleteDraft_ThrowsInvalidStatus_WhenDocumentNotDraft() {
        Long userId = 100L;
        Long docId = 1L;

        UserLogin creator = UserLogin.builder().id(userId).username("author").role(Role.USER).build();
        Document doc = Document.builder()
                .id(docId)
                .draftCode("DT-2026-00001")
                .creatorUser(creator)
                .status(DocumentStatus.PENDING_REVIEW)
                .build();

        when(userLoginRepository.findByIdAndDeleteFlgFalse(userId)).thenReturn(Optional.of(creator));
        when(documentRepository.findByIdAndDeleteFlgFalse(docId)).thenReturn(Optional.of(doc));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentDraftService.deleteDraft(docId, userId));
        assertEquals(ErrorCode.DOCUMENT_INVALID_STATUS, ex.getErrorCode());
    }
}

