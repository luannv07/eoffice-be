package vn.edu.epu.eoffice_be.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.config.JpaAuditingConfig;
import vn.edu.epu.eoffice_be.common.enums.ApprovalRequestStatus;
import vn.edu.epu.eoffice_be.common.enums.DocumentProcessAction;
import vn.edu.epu.eoffice_be.common.enums.DocumentSecurityLevel;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.DocumentUrgencyLevel;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentApprovalRequest;
import vn.edu.epu.eoffice_be.entity.DocumentAttachment;
import vn.edu.epu.eoffice_be.entity.DocumentProcessLog;
import vn.edu.epu.eoffice_be.entity.DocumentVersion;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.UserLogin;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:docrepo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
@Transactional
class DocumentRepositoryTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserLoginRepository userLoginRepository;

    @Autowired
    private OrgNodeRepository orgNodeRepository;

    @Autowired
    private DocumentProcessLogRepository documentProcessLogRepository;

    @Autowired
    private PositionAssignmentRepository positionAssignmentRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private DocumentAttachmentRepository documentAttachmentRepository;

    @Autowired
    private DocumentApprovalRequestRepository documentApprovalRequestRepository;

    private OrgNode getOrCreateOrgNode(String code) {
        return orgNodeRepository.findByCodeAndDeleteFlgFalse(code)
                .orElseGet(() -> orgNodeRepository.saveAndFlush(OrgNode.builder()
                        .code(code)
                        .nameVn("Phòng Hành chính Tổng hợp")
                        .nameEn("General Administration Department")
                        .typeCd("PHONG")
                        .build()));
    }

    @Test
    @DisplayName("searchDocuments hoạt động chính xác khi keyword là null hoặc rỗng")
    void searchDocuments_WithNullOrBlankKeyword_ReturnsAllMatching() {
        OrgNode orgNode = getOrCreateOrgNode("TEST_ORG_1");

        UserLogin creator = userLoginRepository.saveAndFlush(UserLogin.builder()
                .username("test-repo-user-1")
                .passwordHash("secret")
                .build());

        Document doc = Document.builder()
                .draftCode("DT-TEST-001")
                .title("Kế hoạch đào tạo 2026")
                .summary("Trích yếu đào tạo")
                .content("Nội dung đào tạo")
                .documentTypeCd("CONG_VAN")
                .status(DocumentStatus.DRAFT)
                .securityLevel(DocumentSecurityLevel.NORMAL)
                .urgencyLevel(DocumentUrgencyLevel.NORMAL)
                .currentVersion(1)
                .creatorUser(creator)
                .issuingOrgNode(orgNode)
                .deleteFlg(false)
                .build();
        documentRepository.saveAndFlush(doc);

        // 1. Khi keyword = null
        Page<Document> pageNull = documentRepository.searchDocuments(
                creator.getId(), null, null, null, PageRequest.of(0, 10));
        assertTrue(pageNull.getTotalElements() >= 1);
        assertTrue(pageNull.getContent().stream().anyMatch(d -> d.getDraftCode().equals("DT-TEST-001")));

        // 2. Khi keyword = "" (chuỗi rỗng)
        Page<Document> pageEmpty = documentRepository.searchDocuments(
                creator.getId(), null, null, "", PageRequest.of(0, 10));
        assertTrue(pageEmpty.getTotalElements() >= 1);
        assertTrue(pageEmpty.getContent().stream().anyMatch(d -> d.getDraftCode().equals("DT-TEST-001")));
    }

    @Test
    @DisplayName("searchDocuments tìm kiếm với ký tự đại diện escaped (%) và (_)")
    void searchDocuments_WithEscapedWildcards_MatchesExactOnly() {
        OrgNode orgNode = getOrCreateOrgNode("TEST_ORG_2");

        UserLogin creator = userLoginRepository.saveAndFlush(UserLogin.builder()
                .username("test-repo-user-2")
                .passwordHash("secret")
                .build());

        // Doc 1: chứa literal % trong title
        Document doc1 = Document.builder()
                .draftCode("DT-TEST-002")
                .title("Báo cáo 100% hoàn thành")
                .summary("Trích yếu")
                .content("Nội dung")
                .documentTypeCd("CONG_VAN")
                .status(DocumentStatus.DRAFT)
                .securityLevel(DocumentSecurityLevel.NORMAL)
                .urgencyLevel(DocumentUrgencyLevel.NORMAL)
                .currentVersion(1)
                .creatorUser(creator)
                .issuingOrgNode(orgNode)
                .deleteFlg(false)
                .build();
        documentRepository.saveAndFlush(doc1);

        // Doc 2: chứa 1000 hoàn thành (không có %)
        Document doc2 = Document.builder()
                .draftCode("DT-TEST-003")
                .title("Báo cáo 1000 hoàn thành")
                .summary("Trích yếu")
                .content("Nội dung")
                .documentTypeCd("CONG_VAN")
                .status(DocumentStatus.DRAFT)
                .securityLevel(DocumentSecurityLevel.NORMAL)
                .urgencyLevel(DocumentUrgencyLevel.NORMAL)
                .currentVersion(1)
                .creatorUser(creator)
                .issuingOrgNode(orgNode)
                .deleteFlg(false)
                .build();
        documentRepository.saveAndFlush(doc2);

        // Tìm với keyword escaped "100\\%" -> chỉ khớp doc1, không khớp doc2!
        Page<Document> page = documentRepository.searchDocuments(
                creator.getId(), null, null, "100\\%", PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals("DT-TEST-002", page.getContent().get(0).getDraftCode());
    }

    @Test
    @DisplayName("Kiểm tra sequence documents_seq với allocationSize=50 không gây trùng khóa chính khi lưu nhiều bản ghi")
    void documentsSequence_AllocationSize50_NoPrimaryKeyCollision() {
        OrgNode orgNode = getOrCreateOrgNode("TEST_ORG_SEQ");
        UserLogin creator = userLoginRepository.saveAndFlush(UserLogin.builder()
                .username("test-seq-user")
                .passwordHash("secret")
                .build());

        java.util.Set<Long> generatedIds = new java.util.HashSet<>();
        // Lưu 55 bản ghi liên tiếp để vượt ngưỡng allocationSize=50
        for (int i = 1; i <= 55; i++) {
            Document doc = Document.builder()
                    .draftCode("DT-SEQ-" + String.format("%03d", i))
                    .title("Văn bản kiểm tra sequence " + i)
                    .summary("Trích yếu kiểm tra sequence " + i)
                    .content("Nội dung chi tiết " + i)
                    .documentTypeCd("CONG_VAN")
                    .status(DocumentStatus.DRAFT)
                    .securityLevel(DocumentSecurityLevel.NORMAL)
                    .urgencyLevel(DocumentUrgencyLevel.NORMAL)
                    .currentVersion(1)
                    .creatorUser(creator)
                    .issuingOrgNode(orgNode)
                    .deleteFlg(false)
                    .build();
            Document saved = documentRepository.saveAndFlush(doc);
            assertNotNull(saved.getId(), "ID không được null");
            assertTrue(generatedIds.add(saved.getId()), "ID " + saved.getId() + " đã bị trùng lặp!");
        }
        assertEquals(55, generatedIds.size());
    }

    @Test
    @DisplayName("Kiểm tra Document không expose public setter cho cờ xóa mềm deleteFlg")
    void document_DoesNotExposeDeleteFlgSetter() {
        assertThrows(NoSuchMethodException.class, () -> Document.class.getMethod("setDeleteFlg", boolean.class));
        assertThrows(NoSuchMethodException.class, () -> Document.class.getMethod("setDeleteFlg", Boolean.class));
    }

    @Test
    @DisplayName("Kiểm tra DocumentProcessLog tạo bằng no-args constructor không bị lỗi NOT NULL logTime khi persist")
    void documentProcessLog_NoArgsConstructor_PersistsSuccessfully() {
        OrgNode orgNode = getOrCreateOrgNode("TEST_ORG_LOG");
        UserLogin actor = userLoginRepository.saveAndFlush(UserLogin.builder()
                .username("test-log-user")
                .passwordHash("secret")
                .build());

        Document doc = documentRepository.saveAndFlush(Document.builder()
                .draftCode("DT-LOG-001")
                .title("Văn bản test log")
                .summary("Trích yếu")
                .content("Nội dung")
                .documentTypeCd("CONG_VAN")
                .status(DocumentStatus.DRAFT)
                .securityLevel(DocumentSecurityLevel.NORMAL)
                .urgencyLevel(DocumentUrgencyLevel.NORMAL)
                .currentVersion(1)
                .creatorUser(actor)
                .issuingOrgNode(orgNode)
                .deleteFlg(false)
                .build());

        DocumentProcessLog log = new DocumentProcessLog();

        log.setDocument(doc);
        log.setActorUser(actor);
        log.setAction(DocumentProcessAction.CREATE_DRAFT);
        log.setNote("Ghi chú thử nghiệm");

        DocumentProcessLog savedLog = documentProcessLogRepository.saveAndFlush(log);
        assertNotNull(savedLog.getId());
        assertNotNull(savedLog.getLogTime());
        assertNotEquals(Boolean.TRUE, savedLog.isDeleteFlg());
    }

    @Test
    @DisplayName("findActiveAssignmentsByPositionCds trả về rỗng an toàn khi collection null hoặc rỗng, không sinh lỗi SQL IN ()")
    void findActiveAssignmentsByPositionCds_WithNullOrEmpty_ReturnsEmptyListWithoutSqlError() {
        java.time.Instant now = java.time.Instant.now();

        var resultNull = positionAssignmentRepository.findActiveAssignmentsByPositionCds(null, now);
        assertNotNull(resultNull);
        assertTrue(resultNull.isEmpty());

        var resultEmpty = positionAssignmentRepository.findActiveAssignmentsByPositionCds(java.util.Collections.emptyList(), now);
        assertNotNull(resultEmpty);
        assertTrue(resultEmpty.isEmpty());
    }

    @Test
    @DisplayName("document_versions: Lưu và truy vấn phiên bản văn bản thành công từ CSDL")
    void documentVersionRepository_SaveAndQuery_Success() {
        OrgNode orgNode = getOrCreateOrgNode("TEST_ORG_VER");
        UserLogin author = userLoginRepository.saveAndFlush(UserLogin.builder()
                .username("test-ver-author")
                .passwordHash("secret")
                .build());

        Document doc = documentRepository.saveAndFlush(Document.builder()
                .draftCode("DT-TEST-VER-001")
                .title("Văn bản kiểm tra phiên bản")
                .summary("Trích yếu phiên bản")
                .documentTypeCd("QUYET_DINH")
                .status(DocumentStatus.DRAFT)
                .securityLevel(DocumentSecurityLevel.NORMAL)
                .urgencyLevel(DocumentUrgencyLevel.NORMAL)
                .currentVersion(1)
                .creatorUser(author)
                .issuingOrgNode(orgNode)
                .deleteFlg(false)
                .build());

        DocumentVersion ver1 = documentVersionRepository.saveAndFlush(DocumentVersion.builder()
                .document(doc)
                .versionNumber(1)
                .title("Tiêu đề bản 1")
                .summary("Trích yếu bản 1")
                .content("Nội dung bản 1")
                .createdByUser(author)
                .deleteFlg(false)
                .build());

        DocumentVersion ver2 = documentVersionRepository.saveAndFlush(DocumentVersion.builder()
                .document(doc)
                .versionNumber(2)
                .title("Tiêu đề bản 2")
                .summary("Trích yếu bản 2")
                .content("Nội dung bản 2")
                .changeSummary("Cập nhật điều 2")
                .createdByUser(author)
                .deleteFlg(false)
                .build());

        assertNotNull(ver1.getId());
        assertNotNull(ver2.getId());

        var versions = documentVersionRepository.findByDocumentIdAndDeleteFlgFalseOrderByVersionNumberDesc(doc.getId());
        assertEquals(2, versions.size());
        assertEquals(2, versions.get(0).getVersionNumber());

        var topVer = documentVersionRepository.findTopByDocumentIdAndDeleteFlgFalseOrderByVersionNumberDesc(doc.getId());
        assertTrue(topVer.isPresent());
        assertEquals(2, topVer.get().getVersionNumber());

        var v1Opt = documentVersionRepository.findByDocumentIdAndVersionNumberAndDeleteFlgFalse(doc.getId(), 1);
        assertTrue(v1Opt.isPresent());
        assertEquals("Tiêu đề bản 1", v1Opt.get().getTitle());
    }

    @Test
    @DisplayName("document_attachments: Lưu và truy vấn tệp đính kèm văn bản thành công từ CSDL")
    void documentAttachmentRepository_SaveAndQuery_Success() {
        OrgNode orgNode = getOrCreateOrgNode("TEST_ORG_ATT");
        UserLogin author = userLoginRepository.saveAndFlush(UserLogin.builder()
                .username("test-att-author")
                .passwordHash("secret")
                .build());

        Document doc = documentRepository.saveAndFlush(Document.builder()
                .draftCode("DT-TEST-ATT-001")
                .title("Văn bản kiểm tra tệp đính kèm")
                .summary("Trích yếu tệp đính kèm")
                .documentTypeCd("CONG_VAN")
                .status(DocumentStatus.DRAFT)
                .securityLevel(DocumentSecurityLevel.NORMAL)
                .urgencyLevel(DocumentUrgencyLevel.NORMAL)
                .currentVersion(1)
                .creatorUser(author)
                .issuingOrgNode(orgNode)
                .deleteFlg(false)
                .build());

        DocumentAttachment mainAtt = documentAttachmentRepository.saveAndFlush(DocumentAttachment.builder()
                .document(doc)
                .originalFileName("kehoach.docx")
                .storageFileName("storage-uuid-1.docx")
                .storagePath("uploads/2026/09/1")
                .fileExtension("docx")
                .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .fileSizeBytes(2048L)
                .sha256Checksum("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .isMainDocument(true)
                .uploadedByUser(author)
                .deleteFlg(false)
                .build());

        DocumentAttachment subAtt = documentAttachmentRepository.saveAndFlush(DocumentAttachment.builder()
                .document(doc)
                .originalFileName("phuluc.pdf")
                .storageFileName("storage-uuid-2.pdf")
                .storagePath("uploads/2026/09/1")
                .fileExtension("pdf")
                .mimeType("application/pdf")
                .fileSizeBytes(1024L)
                .sha256Checksum("d41d8cd98f00b204e9800998ecf8427e")
                .isMainDocument(false)
                .uploadedByUser(author)
                .deleteFlg(false)
                .build());

        assertNotNull(mainAtt.getId());
        assertNotNull(subAtt.getId());

        assertTrue(documentAttachmentRepository.existsByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(doc.getId()));
        assertTrue(documentAttachmentRepository.existsByDocumentIdAndDeleteFlgFalse(doc.getId()));

        var allAttachments = documentAttachmentRepository.findByDocumentIdAndDeleteFlgFalse(doc.getId());
        assertEquals(2, allAttachments.size());

        var mainList = documentAttachmentRepository.findByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(doc.getId());
        assertEquals(1, mainList.size());
        assertEquals("kehoach.docx", mainList.get(0).getOriginalFileName());
    }

    @Test
    @DisplayName("document_approval_requests: Lưu và truy vấn yêu cầu duyệt văn bản thành công từ CSDL")
    void documentApprovalRequestRepository_SaveAndQuery_Success() {
        OrgNode orgNode = getOrCreateOrgNode("TEST_ORG_APR");
        UserLogin sender = userLoginRepository.saveAndFlush(UserLogin.builder()
                .username("test-apr-sender")
                .passwordHash("secret")
                .build());
        UserLogin approver = userLoginRepository.saveAndFlush(UserLogin.builder()
                .username("test-apr-approver")
                .passwordHash("secret")
                .build());

        Document doc = documentRepository.saveAndFlush(Document.builder()
                .draftCode("DT-TEST-APR-001")
                .title("Văn bản kiểm tra yêu cầu duyệt")
                .summary("Trích yếu yêu cầu duyệt")
                .documentTypeCd("TO_TRINH")
                .status(DocumentStatus.WAITING_APPROVAL)
                .securityLevel(DocumentSecurityLevel.NORMAL)
                .urgencyLevel(DocumentUrgencyLevel.NORMAL)
                .currentVersion(1)
                .creatorUser(sender)
                .issuingOrgNode(orgNode)
                .deleteFlg(false)
                .build());

        DocumentApprovalRequest req = documentApprovalRequestRepository.saveAndFlush(DocumentApprovalRequest.builder()
                .document(doc)
                .senderUser(sender)
                .approverUser(approver)
                .status(ApprovalRequestStatus.PENDING)
                .submitNote("Kính gửi lãnh đạo phê duyệt tờ trình")
                .submittedAt(java.time.Instant.now())
                .deleteFlg(false)
                .build());

        assertNotNull(req.getId());

        var list = documentApprovalRequestRepository.findByDocumentIdAndDeleteFlgFalseOrderBySubmittedAtDesc(doc.getId());
        assertEquals(1, list.size());
        assertEquals(ApprovalRequestStatus.PENDING, list.get(0).getStatus());
        assertEquals("Kính gửi lãnh đạo phê duyệt tờ trình", list.get(0).getSubmitNote());

        var pendingOpt = documentApprovalRequestRepository
                .findTopByDocumentIdAndStatusAndDeleteFlgFalseOrderBySubmittedAtDesc(doc.getId(), ApprovalRequestStatus.PENDING);
        assertTrue(pendingOpt.isPresent());
        assertEquals(approver.getId(), pendingOpt.get().getApproverUser().getId());
    }

    @Test
    @DisplayName("findByStatusIn: Kiểm tra phân trang ổn định (ORDER BY xác định) và tính nhất quán giữa List và Page")
    void findByStatusIn_PaginationAndFilterConsistency_Success() {
        documentRepository.deleteAll();

        OrgNode orgNode = getOrCreateOrgNode("TEST_ORG_PAGE");
        UserLogin creator = userLoginRepository.saveAndFlush(UserLogin.builder()
                .username("test-page-user")
                .passwordHash("secret")
                .build());

        for (int i = 1; i <= 4; i++) {
            documentRepository.saveAndFlush(Document.builder()
                    .draftCode("DT-PAGE-00" + i)
                    .title("Văn bản phân trang " + i)
                    .summary("Trích yếu " + i)
                    .documentTypeCd("CONG_VAN")
                    .status(DocumentStatus.WAITING_APPROVAL)
                    .securityLevel(DocumentSecurityLevel.NORMAL)
                    .urgencyLevel(DocumentUrgencyLevel.NORMAL)
                    .currentVersion(1)
                    .creatorUser(creator)
                    .issuingOrgNode(orgNode)
                    .deleteFlg(false)
                    .build());
        }

        List<DocumentStatus> statuses = List.of(DocumentStatus.WAITING_APPROVAL);

        // [5/17] Xác nhận phân trang ổn định xác định (ORDER BY d.id ASC) kể cả khi Pageable không truyền Sort
        Page<Document> page0 = documentRepository.findByStatusIn(statuses, PageRequest.of(0, 2));
        Page<Document> page1 = documentRepository.findByStatusIn(statuses, PageRequest.of(1, 2));

        assertEquals(4, page0.getTotalElements());
        assertEquals(2, page0.getContent().size());
        assertEquals(2, page1.getContent().size());

        List<Long> idsPage0 = page0.getContent().stream().map(Document::getId).toList();
        List<Long> idsPage1 = page1.getContent().stream().map(Document::getId).toList();

        assertTrue(idsPage0.get(0) < idsPage0.get(1));
        assertTrue(idsPage1.get(0) < idsPage1.get(1));
        assertTrue(idsPage0.get(1) < idsPage1.get(0));

        java.util.Set<Long> allPagedIds = new java.util.HashSet<>();
        allPagedIds.addAll(idsPage0);
        allPagedIds.addAll(idsPage1);
        assertEquals(4, allPagedIds.size());

        // [17/20] So sánh tập hợp ID với findByStatusIn(statuses) để tránh phụ thuộc vào thứ tự danh sách không đảm bảo
        List<Document> listResults = documentRepository.findByStatusIn(statuses);
        assertEquals(4, listResults.size());
        java.util.Set<Long> listIdsSet = listResults.stream().map(Document::getId).collect(java.util.stream.Collectors.toSet());
        assertEquals(allPagedIds, listIdsSet);
    }

    @Test
    @DisplayName("findByStatusIn: Kiểm tra phân trang với PageRequest unsorted duy trì thứ tự id ASC xác định")
    void findByStatusIn_UnsortedPageable_MaintainsDeterministicAscendingOrder_Success() {
        documentRepository.deleteAll();

        OrgNode orgNode = getOrCreateOrgNode("TEST_ORG_PAGE_UNSORTED");
        UserLogin creator = userLoginRepository.saveAndFlush(UserLogin.builder()
                .username("test-page-unsorted-user")
                .passwordHash("secret")
                .build());

        List<Long> createdIds = new java.util.ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Document saved = documentRepository.saveAndFlush(Document.builder()
                    .draftCode("DT-PAGE-UNSORTED-00" + i)
                    .title("Văn bản phân trang unsorted " + i)
                    .documentTypeCd("CONG_VAN")
                    .status(DocumentStatus.WAITING_APPROVAL)
                    .securityLevel(DocumentSecurityLevel.NORMAL)
                    .urgencyLevel(DocumentUrgencyLevel.NORMAL)
                    .currentVersion(1)
                    .creatorUser(creator)
                    .issuingOrgNode(orgNode)
                    .deleteFlg(false)
                    .build());
            createdIds.add(saved.getId());
        }

        List<DocumentStatus> statuses = List.of(DocumentStatus.WAITING_APPROVAL);
        Page<Document> page = documentRepository.findByStatusIn(statuses, PageRequest.of(0, 3));

        List<Long> ids = page.getContent().stream().map(Document::getId).toList();
        assertEquals(3, ids.size());
        assertEquals(createdIds, ids);
        assertEquals(3, page.getTotalElements());
        assertEquals(1, page.getTotalPages());
        assertTrue(ids.get(0) < ids.get(1));
        assertTrue(ids.get(1) < ids.get(2));
    }

    @Test
    @DisplayName("findByStatusIn: Kiểm tra với collection status rỗng trả về rỗng an toàn")
    void findByStatusIn_EmptyStatuses_ReturnsEmptySafely() {
        List<Document> emptyList = documentRepository.findByStatusIn(List.of());
        assertNotNull(emptyList);
        assertTrue(emptyList.isEmpty());

        Page<Document> emptyPage = documentRepository.findByStatusIn(List.of(), PageRequest.of(0, 10));
        assertNotNull(emptyPage);
        assertTrue(emptyPage.isEmpty());
    }
}
