package vn.edu.epu.eoffice_be.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.config.JpaAuditingConfig;
import vn.edu.epu.eoffice_be.common.enums.DocumentProcessAction;
import vn.edu.epu.eoffice_be.common.enums.DocumentSecurityLevel;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.DocumentUrgencyLevel;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentProcessLog;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.UserLogin;

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
        assertFalse(Boolean.TRUE.equals(savedLog.isDeleteFlg()));
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
}
