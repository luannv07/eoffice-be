package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.config.UploadProperties;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.security.pki.PdfSigner;
import vn.edu.epu.eoffice_be.storage.DocumentPdfReference;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageProviderRegistry;
import vn.edu.epu.eoffice_be.storage.provider.FileSystemFileStorageProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Kiểm thử API thẩm định với DB riêng và bộ ký giả lập.
@SpringBootTest(properties="spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ReviewInboxIntegrationTest {
    static final String DB="jdbc:h2:mem:review-inbox-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH;LOCK_TIMEOUT=8000";

    // Dùng H2 mặc định hoặc database PostgreSQL cục bộ dành riêng cho kiểm thử.
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("EOFFICE_TASK3_TEST_JDBC_URL");
        if (url == null || url.isBlank()) {
            registry.add("spring.datasource.url", () -> DB);
            return;
        }
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/eoffice_task3_test")) {
            throw new IllegalArgumentException("Chỉ dùng PostgreSQL cục bộ với database eoffice_task3_test");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv("EOFFICE_TASK3_TEST_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("EOFFICE_TASK3_TEST_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.liquibase.change-log", () -> "classpath:db/changelog/master.yml");
    }
    @Autowired UserLoginRepository users;
    @Autowired OrgNodeRepository orgs;
    @Autowired PositionAssignmentRepository positions;
    @Autowired PositionPermissionRepository permissions;
    @Autowired PositionDegRepository delegations;
    @Autowired PermissionResolver resolver;
    @Autowired DocumentRepository documents;
    @Autowired DocumentApprovalRequestRepository requests;
    @SpyBean DocumentWorkflowLogRepository logs;
    @Autowired DocumentAttachmentRepository attachments;
    @Autowired DocumentProcessLogRepository processLogs;
    @Autowired StoredFileMetadataRepository storageMetadata;
    @Autowired FileStorageService legacyStorage;
    @Autowired UploadProperties uploadProperties;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockBean PdfSigner signer;
    @MockBean DocumentPdfStorageService pdfStorage;
    TransactionTemplate tx;
    UserLogin author,dean,vice,outsider,admin;
    OrgNode faculty;
    static UserLogin rector;
    static OrgNode school;
    Long id;
    final Instant time=Instant.parse("2026-11-01T00:00:00Z");
    // Thiet lap du lieu co ban cho moi ca kiem thu
    @BeforeEach void setup() {
        tx=new TransactionTemplate(manager);
        tx.executeWithoutResult(s->{
            faculty=orgs.saveAndFlush(OrgNode.builder().code("RI-"+UUID.randomUUID()).nameVn("Faculty").nameEn("Faculty").typeCd("FACULTY").build());
            author=user(Role.USER);dean=user(Role.REVIEWER);vice=user(Role.REVIEWER);outsider=user(Role.REVIEWER);admin=user(Role.ADMIN);
            for(var pair:List.of(Map.entry(dean,"TK"),Map.entry(vice,"PK"))) {
                positions.saveAndFlush(PositionAssignment.builder().userLogin(pair.getKey()).orgNode(faculty)
                        .positionCd(pair.getValue()).effectiveFrom(Instant.now().minusSeconds(60)).build());
                for(String code:List.of("DOCUMENT_SIGN_DRAFT","DOCUMENT_APPROVE")) {
                    var p=permissions.findByPositionCdAndPermissionCdAndDeleteFlgFalse(pair.getValue(),code)
                            .orElseGet(()->PositionPermission.builder().positionCd(pair.getValue()).permissionCd(code).build());
                    p.setInheritToChildren(false);permissions.saveAndFlush(p);
                }
            }
            // Khoi tao lanh dao HT duy nhat 1 lan cho database H2 dung chung
            if (rector == null) {
                var existingHt = positions.findAll().stream()
                        .filter(p -> "HT".equals(p.getPositionCd()) && !p.isDeleteFlg())
                        .findFirst();
                if (existingHt.isPresent()) {
                    rector = existingHt.get().getUserLogin();
                    school = existingHt.get().getOrgNode();
                } else {
                    school = orgs.saveAndFlush(OrgNode.builder().code("S-" + UUID.randomUUID())
                            .nameVn("University").nameEn("University").typeCd("UNIVERSITY").build());
                    rector = user(Role.LEADER);
                    positions.saveAndFlush(PositionAssignment.builder().userLogin(rector).orgNode(school)
                            .positionCd("HT").effectiveFrom(Instant.now().minusSeconds(60)).build());
                }
            }
            id=document(DocumentStatus.IN_REVIEW,false).getId();
        });
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    UserLogin user(Role role){return users.saveAndFlush(UserLogin.builder().username("ri-"+UUID.randomUUID()).passwordHash("sample").role(role).build());}
    Document document(DocumentStatus status,boolean deleted){return documents.saveAndFlush(Document.builder().title("Review inbox")
            .summary("Summary").documentTypeCd("CONG_VAN").drafter(author).issuingOrgNode(faculty).issuingOrg(faculty).status(status).deleteFlg(deleted)
            .primaryFilePath("storage://101").build());}
    DocumentApprovalRequest request(Long documentId,UserLogin receiver,ApprovalRequestStatus status,boolean deleted,Instant at){
        return tx.execute(s->requests.saveAndFlush(DocumentApprovalRequest.builder().document(documents.getReferenceById(documentId))
                .senderUser(author).approverUser(receiver).status(status).deleteFlg(deleted).submittedAt(at).build()));
    }
    // Tao token xac thuc cho nguoi dung voi quyen mac dinh REVIEWER
    UsernamePasswordAuthenticationToken auth(UserLogin user){return new UsernamePasswordAuthenticationToken(
            new JwtUserPrincipal(user.getId(),user.getUsername(),"REVIEWER",true,List.of()),null,List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));}
    // Tao token xac thuc cho nguoi dung voi vai tro cu the
    UsernamePasswordAuthenticationToken authWithRole(UserLogin user, Role role){
        String r = role != null ? role.name() : "REVIEWER";
        return new UsernamePasswordAuthenticationToken(
                new JwtUserPrincipal(user.getId(), user.getUsername(), r, true, List.of()), null, List.of(new SimpleGrantedAuthority("ROLE_" + r)));
    }
    // Gia lap doc va ky so PDF phuc vu kiem thu
    void signs(UserLogin user){
        byte[] signedPdf = new byte[]{3,4};
        when(pdfStorage.readPdf("storage://101")).thenReturn(new byte[]{1,2});
        when(pdfStorage.readPdf("storage://102")).thenReturn(signedPdf);
        when(signer.signBytes(any(),eq(user.getId().toString()),anyString(),anyString())).thenReturn(
                new PdfSigner.SignResult(user.getId().toString(),"reviewer","serial","hash",new Date(),"field",null,signedPdf));
        when(pdfStorage.storeSignedPdf(any(),anyString(),eq(id))).thenReturn("storage://102");
    }
    @ParameterizedTest @ValueSource(strings={"initial-sign","approve","request-revision","reject"})
    void viceWithRealPermissionCannotProcessDocumentAssignedToDean(String action) throws Exception {
        request(id,dean,ApprovalRequestStatus.PENDING,false,time);
        assertTrue(resolver.hasPermission(vice.getId(),vice.getRole(),"DOCUMENT_SIGN_DRAFT",faculty.getId()));
        assertTrue(resolver.hasPermission(vice.getId(),vice.getRole(),"DOCUMENT_APPROVE",faculty.getId()));
        mvc.perform(post("/documents/"+id+"/workflow/"+action).with(authentication(auth(vice)))
                .contentType("application/json").content("{\"comment\":\"review\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        assertEquals(DocumentStatus.IN_REVIEW,documents.findById(id).orElseThrow().getStatus());
        assertEquals("storage://101",documents.findById(id).orElseThrow().getPrimaryFilePath());
        assertTrue(logs.findByDocumentIdOrderByCreatedDateAsc(id).isEmpty());
        verifyNoInteractions(signer,pdfStorage);
    }
    // Chi nguoi duoc phan cong moi co quyen xu ly van ban
    @ParameterizedTest @CsvSource({"false,initial-sign,IN_REVIEW","true,initial-sign,IN_REVIEW",
            "false,approve,PENDING_APPROVAL","true,approve,PENDING_APPROVAL",
            "false,request-revision,NEEDS_REVISION","true,request-revision,NEEDS_REVISION",
            "false,reject,REJECTED","true,reject,REJECTED"})
    void onlySelectedDeanOrViceCanProcess(boolean selectVice,String action,DocumentStatus expected) throws Exception {
        UserLogin selected=selectVice?vice:dean;
        request(id,selected,ApprovalRequestStatus.PENDING,false,time);
        if(action.equals("initial-sign")||action.equals("approve")) signs(selected);
        // Ky nhay truoc khi phe duyet de thoa man dieu kien kiem tra chu ky nhay
        if(action.equals("approve")) {
            mvc.perform(post("/documents/"+id+"/workflow/initial-sign").with(authentication(auth(selected)))
                    .contentType("application/json").content("{\"comment\":\"initial sign\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/documents/"+id+"/workflow/"+action).with(authentication(auth(selected)))
                .contentType("application/json").content("{\"comment\":\"review\"}"))
                .andExpect(status().isOk());
        assertEquals(expected,documents.findById(id).orElseThrow().getStatus());
        // Phe duyet ban giao se tao them 1 yeu cau cho lanh dao, cac hanh dong khac giu nguyen 1
        assertEquals(action.equals("approve") ? 2 : 1, requests.findByDocumentIdAndDeleteFlgFalseOrderBySubmittedAtDesc(id).size());
    }
    @Test void currentInboxIsSeparateFromHistoricalReadAndAdminReadAll() throws Exception {
        request(id,vice,ApprovalRequestStatus.APPROVED,false,time.minusSeconds(1));
        request(id,dean,ApprovalRequestStatus.PENDING,false,time);
        mvc.perform(get("/documents/review-inbox").with(authentication(auth(dean))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(id));
        for(var viewer:List.of(vice,author,admin)) {
            mvc.perform(get("/documents/review-inbox").with(authentication(auth(viewer))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
            mvc.perform(get("/documents/"+id).with(authentication(auth(viewer)))).andExpect(status().isOk());
        }
        mvc.perform(get("/documents/"+id).with(authentication(auth(outsider)))).andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings={"deleted","approved","rejected","recalled"})
    void latestInvalidRequestCannotReviveOlderPendingAssignment(String latest) throws Exception {
        request(id,dean,ApprovalRequestStatus.PENDING,false,time.minusSeconds(1));
        request(id,vice,latest.equals("deleted")?ApprovalRequestStatus.PENDING:ApprovalRequestStatus.valueOf(latest.toUpperCase(Locale.ROOT)),latest.equals("deleted"),time);
        for(var viewer:List.of(dean,vice)) mvc.perform(get("/documents/review-inbox").with(authentication(auth(viewer))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(post("/documents/"+id+"/workflow/initial-sign").with(authentication(auth(dean)))
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_WORKFLOW_STATE"));
        verifyNoInteractions(signer,pdfStorage);
    }
    @Test void sameSubmissionTimeUsesIdAsStableTieBreaker() throws Exception {
        var first=request(id,dean,ApprovalRequestStatus.PENDING,false,time);
        var last=request(id,vice,ApprovalRequestStatus.PENDING,false,time);
        assertTrue(last.getId()>first.getId());
        assertEquals(last.getId(),requests.findFirstByDocumentIdOrderBySubmittedAtDescIdDesc(id).orElseThrow().getId());
        mvc.perform(get("/documents/review-inbox").with(authentication(auth(dean))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/documents/review-inbox").with(authentication(auth(vice))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
    }
    @Test void changedAssignmentAfterLoadingInboxIsRecheckedBeforeSigning() throws Exception {
        request(id,dean,ApprovalRequestStatus.PENDING,false,time);
        mvc.perform(get("/documents/review-inbox").with(authentication(auth(dean)))).andExpect(jsonPath("$.data.totalElements").value(1));
        request(id,vice,ApprovalRequestStatus.PENDING,false,time.plusSeconds(1));
        mvc.perform(post("/documents/"+id+"/workflow/initial-sign").with(authentication(auth(dean)))
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(signer,pdfStorage);
    }
    @Test void validDelegationDoesNotOverrideNamedReceiver() throws Exception {
        request(id,dean,ApprovalRequestStatus.PENDING,false,time);
        tx.executeWithoutResult(s->{
            var a=positions.findActiveAssignments(vice.getId(),Instant.now()).getFirst();
            a.setDeleteFlg(true);positions.saveAndFlush(a);
            delegations.saveAndFlush(PositionDeg.builder().orgNode(faculty).permissionCd("DOCUMENT_SIGN_DRAFT")
                    .delegator(dean).delegatee(vice).effectiveFrom(Instant.now().minusSeconds(60)).effectiveTo(Instant.now().plusSeconds(3600)).build());
        });
        assertTrue(resolver.hasPermission(vice.getId(),vice.getRole(),"DOCUMENT_SIGN_DRAFT",faculty.getId()));
        mvc.perform(post("/documents/"+id+"/workflow/initial-sign").with(authentication(auth(vice)))
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(signer,pdfStorage);
    }
    @Test void inboxFiltersBeforePagingWithCorrectCountsAndNoHistoricalDuplicates() throws Exception {
        List<Long> expected=new ArrayList<>();
        for(int i=0;i<5;i++) {
            Long doc=tx.execute(s->document(DocumentStatus.IN_REVIEW,false).getId());expected.addFirst(doc);
            request(doc,vice,ApprovalRequestStatus.PENDING,false,time.minusSeconds(1));
            request(doc,dean,ApprovalRequestStatus.PENDING,false,time.plusSeconds(i));
        }
        for(var state:List.of(DocumentStatus.DRAFT,DocumentStatus.PENDING_APPROVAL,DocumentStatus.PUBLISHED)) {
            Long doc=tx.execute(s->document(state,false).getId());request(doc,dean,ApprovalRequestStatus.PENDING,false,time);
        }
        Long deleted=tx.execute(s->document(DocumentStatus.IN_REVIEW,true).getId());request(deleted,dean,ApprovalRequestStatus.PENDING,false,time);
        for(int page=0;page<3;page++) {
            var result=mvc.perform(get("/documents/review-inbox").param("page",String.valueOf(page)).param("size","2")
                    .with(authentication(auth(dean))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(5))
                    .andExpect(jsonPath("$.data.totalPages").value(3));
            for(int index=0;index<Math.min(2,5-page*2);index++) result.andExpect(jsonPath("$.data.content["+index+"].id").value(expected.get(page*2+index)));
        }
    }
    @Test void disabledAccountAndForgedAdminClaimUseDatabaseState() throws Exception {
        request(id,dean,ApprovalRequestStatus.PENDING,false,time);
        // JWT says reviewer, database says admin: may read globally, cannot process.
        mvc.perform(post("/documents/"+id+"/workflow/initial-sign").with(authentication(auth(admin)))
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        tx.executeWithoutResult(s->{var user=users.findById(dean.getId()).orElseThrow();user.setUserStatusCd("DISABLED");users.saveAndFlush(user);});
        mvc.perform(get("/documents/review-inbox").with(authentication(auth(dean))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("ACCOUNT_DISABLED"));
        mvc.perform(post("/documents/"+id+"/workflow/initial-sign").with(authentication(auth(dean)))
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(signer,pdfStorage);
    }
    @Test void missingRequestHasEmptyInboxAndCannotBeSigned() throws Exception {
        mvc.perform(get("/documents/review-inbox").with(authentication(auth(dean))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(post("/documents/"+id+"/workflow/initial-sign").with(authentication(auth(dean)))
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_WORKFLOW_STATE"));
        verifyNoInteractions(signer,pdfStorage);
    }
    @Test void deletedAccountCannotUseOldJwtToReadInboxOrSign() throws Exception {
        request(id,dean,ApprovalRequestStatus.PENDING,false,time);
        tx.executeWithoutResult(s->{var user=users.findById(dean.getId()).orElseThrow();user.setDeleteFlg(true);users.saveAndFlush(user);});
        mvc.perform(get("/documents/review-inbox").with(authentication(auth(dean))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/documents/"+id+"/workflow/initial-sign").with(authentication(auth(dean)))
                .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(signer,pdfStorage);
    }

    // Tham dinh vien lay danh sach ban giao khi chi co 1 HT (che do AUTO)
    @Test void handoverOptions_returnsSingleRectorInAutoMode() throws Exception {
        request(id, dean, ApprovalRequestStatus.PENDING, false, time);
        mvc.perform(get("/documents/" + id + "/handover-options").with(authentication(auth(dean))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectionMode").value("AUTO"))
                .andExpect(jsonPath("$.data.approvers[0].userId").value(rector.getId()))
                .andExpect(jsonPath("$.data.approvers[0].positionCd").value("HT"));
    }

    // Nguoi soan thao (ROLE_USER) khong duoc phep goi API ban giao
    @Test void handoverOptions_forbiddenForDrafter() throws Exception {
        request(id, dean, ApprovalRequestStatus.PENDING, false, time);
        mvc.perform(get("/documents/" + id + "/handover-options").with(authentication(authWithRole(author, Role.USER))))
                .andExpect(status().isForbidden());
    }

    // Sau khi ban giao, lanh dao nhan doc duoc van ban, nguoi ngoai don vi bi chan
    @Test void handoverToLeaderAllowsLeaderToReadDocumentAndPdf() throws Exception {
        request(id, dean, ApprovalRequestStatus.PENDING, false, time);
        signs(dean);
        mvc.perform(post("/documents/" + id + "/workflow/initial-sign").with(authentication(auth(dean)))
                .contentType("application/json").content("{\"comment\":\"initial\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/documents/" + id + "/workflow/approve").with(authentication(auth(dean)))
                .contentType("application/json").content("{\"comment\":\"approve to rector\"}"))
                .andExpect(status().isOk());

        // Lanh dao nhan ban giao doc van ban thanh cong
        mvc.perform(get("/documents/" + id).with(authentication(authWithRole(rector, Role.LEADER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));

        // Nguoi ngoai don vi khong co quyen xem
        mvc.perform(get("/documents/" + id).with(authentication(auth(outsider))))
                .andExpect(status().isForbidden());
    }

    // Khi co nhieu lanh dao (HT + PHT), chuyen sang SELECT va bat buoc chon nguoi nhan
    @Test void handoverWithMultipleLeadersRequiresSelection() throws Exception {
        request(id, dean, ApprovalRequestStatus.PENDING, false, time);
        UserLogin vicePresident = user(Role.LEADER);
        PositionAssignment pht = positions.saveAndFlush(PositionAssignment.builder()
                .userLogin(vicePresident).orgNode(school).positionCd("PHT")
                .effectiveFrom(Instant.now().minusSeconds(60)).build());
        try {
            // Che do SELECT va tra ve 2 lanh dao
            mvc.perform(get("/documents/" + id + "/handover-options").with(authentication(auth(dean))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.selectionMode").value("SELECT"))
                    .andExpect(jsonPath("$.data.approvers.length()").value(2));

            signs(dean);
            mvc.perform(post("/documents/" + id + "/workflow/initial-sign").with(authentication(auth(dean)))
                    .contentType("application/json").content("{\"comment\":\"initial\"}"))
                    .andExpect(status().isOk());

            // Khong chon nguoi nhan bao loi BAD_REQUEST
            mvc.perform(post("/documents/" + id + "/workflow/approve").with(authentication(auth(dean)))
                    .contentType("application/json").content("{\"comment\":\"missing selection\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));

            // Chon dich danh PHT thi thanh cong
            mvc.perform(post("/documents/" + id + "/workflow/approve").with(authentication(auth(dean)))
                    .contentType("application/json").content("{\"comment\":\"approved to PHT\",\"nextApproverUserId\":" + vicePresident.getId() + "}"))
                    .andExpect(status().isOk());

            assertEquals(DocumentStatus.PENDING_APPROVAL, documents.findById(id).orElseThrow().getStatus());
            var latest = requests.findFirstByDocumentIdOrderBySubmittedAtDescIdDesc(id).orElseThrow();
            assertEquals(vicePresident.getId(), latest.getApproverUser().getId());
            assertEquals(ApprovalRequestStatus.PENDING, latest.getStatus());
        } finally {
            tx.executeWithoutResult(s -> {
                positions.delete(pht);
                positions.flush();
            });
        }
    }

    // Thử thu hồi với từng thao tác thẩm định theo cả hai thứ tự giữ khóa.
    @ParameterizedTest
    @CsvSource({"initial-sign,true", "initial-sign,false", "approve,true", "approve,false",
            "request-revision,true", "request-revision,false", "reject,true", "reject,false"})
    void recallAndReviewUseTheSameDocumentLock(String reviewAction, boolean recallFirst) throws Exception {
        var original = prepareReviewRound();
        if (reviewAction.equals("approve")) {
            assertEquals(200, applyReviewAction("initial-sign").getResponse().getStatus());
        }
        clearInvocations(signer, pdfStorage);
        List<MvcResult> results = recallFirst ? race("recall", reviewAction) : race(reviewAction, "recall");
        assertEquals(200, results.getFirst().getResponse().getStatus());

        // Đọc lại yêu cầu, PDF, tệp chính và log sau khi hai giao dịch đã kết thúc.
        boolean recalled = recallFirst || reviewAction.equals("initial-sign");
        if (recalled) {
            assertStatusAndFiles(DocumentStatus.DRAFT, null, false);
            assertEquals(ApprovalRequestStatus.RECALLED, requests.findById(original.getId()).orElseThrow().getStatus());
            assertEquals(1, processLogs.findByDocumentIdAndDeleteFlgFalseOrderByLogTimeDesc(id).size());
            long expectedSignCount = reviewAction.equals("approve") || !recallFirst ? 1 : 0;
            assertEquals(expectedSignCount, logs.findByDocumentIdOrderByCreatedDateAsc(id).size());
        } else {
            assertEquals(400, results.getLast().getResponse().getStatus());
            assertTrue(processLogs.findByDocumentIdAndDeleteFlgFalseOrderByLogTimeDesc(id).isEmpty());
        }
        if (recallFirst) {
            assertEquals(400, results.getLast().getResponse().getStatus());
            verifyNoInteractions(signer, pdfStorage);
        } else if (reviewAction.equals("initial-sign")) {
            assertEquals(200, results.getLast().getResponse().getStatus());
        } else if (reviewAction.equals("approve")) {
            assertHandover(original);
            verifyNoInteractions(signer);
        } else if (reviewAction.equals("request-revision")) {
            assertStatusAndFiles(DocumentStatus.NEEDS_REVISION, null, false);
            assertEquals(ApprovalRequestStatus.NEEDS_REVISION, requests.findById(original.getId()).orElseThrow().getStatus());
            assertEquals(WorkflowAction.REQUEST_REVISION, logs.findByDocumentIdOrderByCreatedDateAsc(id).getFirst().getAction());
        } else {
            assertStatusAndFiles(DocumentStatus.REJECTED, "storage://101", true);
            assertEquals(ApprovalRequestStatus.REJECTED, requests.findById(original.getId()).orElseThrow().getStatus());
            assertEquals(WorkflowAction.REJECT, logs.findByDocumentIdOrderByCreatedDateAsc(id).getFirst().getAction());
        }
    }

    // Duyệt chỉ chuyển cấp sau khi giao dịch ký ghi thành công bằng chứng.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void signingAndHandoverUseCommittedPdf(boolean signingFirst) throws Exception {
        var original = prepareReviewRound();
        var results = signingFirst ? race("initial-sign", "approve") : race("approve", "initial-sign");
        assertEquals(signingFirst ? 200 : 400, results.getFirst().getResponse().getStatus());
        assertEquals(200, results.getLast().getResponse().getStatus());
        verify(signer).signBytes(any(), anyString(), anyString(), anyString());
        if (signingFirst) {
            assertHandover(original);
        } else {
            assertStatusAndFiles(DocumentStatus.IN_REVIEW, "storage://102", true);
            assertEquals(ApprovalRequestStatus.PENDING, requests.findById(original.getId()).orElseThrow().getStatus());
            assertEquals(1, logs.findByDocumentIdOrderByCreatedDateAsc(id).size());
        }
    }

    // Hai lần bấm duyệt của người thẩm định chỉ tạo một yêu cầu lãnh đạo.
    @Test
    void duplicateHandoverDoesNotRunLeadershipSigner() throws Exception {
        var original = prepareReviewRound();
        assertEquals(200, applyReviewAction("initial-sign").getResponse().getStatus());
        clearInvocations(signer);

        var results = race("approve", "approve");

        assertEquals(200, results.getFirst().getResponse().getStatus());
        assertEquals(400, results.getLast().getResponse().getStatus());
        assertHandover(original);
        verifyNoInteractions(signer);
    }

    // Giữ nguyên dữ liệu khi chính người soạn thử các đường ghi trên hồ sơ đã từ chối.
    @ParameterizedTest
    @EnumSource(Role.class)
    void rejectedRoundKeepsPdfAndBlocksEveryDraftWrite(Role drafterRole) throws Exception {
        author = tx.execute(status -> {
            UserLogin drafter = users.findById(author.getId()).orElseThrow();
            drafter.setRole(drafterRole);
            return users.save(drafter);
        });
        var original = prepareReviewRound();
        assertEquals(200, applyReviewAction("initial-sign").getResponse().getStatus());
        assertEquals(200, applyReviewAction("reject").getResponse().getStatus());
        var asAuthor = authentication(authWithRole(author, drafterRole));
        String path = "/documents/" + id;
        var update = put(path).with(asAuthor).contentType("application/json")
                .content("{\"title\":\"Changed\",\"summary\":\"Changed\",\"documentTypeCd\":\"CONG_VAN\"}");
        var attachment = new MockMultipartFile("file", "changed.pdf", "application/pdf", new byte[]{1});
        var upload = multipart(path + "/attachments").file(attachment).param("isMain", "true").with(asAuthor);
        var main = attachments.findByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(id).getFirst();
        for (var write : List.of(update, upload, delete(path + "/attachments/" + main.getId()).with(asAuthor),
                post(path + "/versions/1/restore").with(asAuthor), delete(path).with(asAuthor))) {
            mvc.perform(write).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("DOCUMENT_INVALID_STATUS"));
        }

        when(pdfStorage.readAsResource("storage://102")).thenReturn(new ByteArrayResource(new byte[]{3, 4}));
        for (var viewer : List.of(author, dean)) {
            mvc.perform(get(path + "/files/primary").with(authentication(authWithRole(viewer, viewer.getRole()))))
                    .andExpect(status().isOk()).andExpect(content().bytes(new byte[]{3, 4}));
        }
        assertStatusAndFiles(DocumentStatus.REJECTED, "storage://102", true);
        var rejected = requests.findById(original.getId()).orElseThrow();
        assertEquals(ApprovalRequestStatus.REJECTED, rejected.getStatus());
        assertEquals("reject", rejected.getDecisionNote());
        assertTrue(processLogs.findByDocumentIdAndDeleteFlgFalseOrderByLogTimeDesc(id).isEmpty());
        assertEquals(2, logs.findByDocumentIdOrderByCreatedDateAsc(id).size());
    }

    // Ép lỗi ghi log sau khi đã lưu PDF và xác nhận cả DB lẫn tệp được khôi phục.
    @Test
    void logWriteFailureRollsBackAndDiscardsSignedFile(@TempDir Path storageDirectory) throws Exception {
        var original = prepareReviewRound();
        var provider = new FileSystemFileStorageProvider(storageDirectory.toString());
        var registry = new FileStorageProviderRegistry(List.of(provider), provider.getName());
        var realStorage = new DocumentPdfStorageService(registry, storageMetadata, legacyStorage, uploadProperties);
        var writtenReference = new AtomicReference<String>();
        doAnswer(call -> {
            String reference = realStorage.storeSignedPdf(call.getArgument(0), call.getArgument(1), id);
            assertArrayEquals(new byte[]{3, 4}, realStorage.readPdf(reference));
            writtenReference.set(reference);
            return reference;
        }).when(pdfStorage).storeSignedPdf(any(), anyString(), eq(id));
        doThrow(new DataIntegrityViolationException("Test ghi log thất bại")).when(logs).save(any(DocumentWorkflowLog.class));

        assertEquals(409, applyReviewAction("initial-sign").getResponse().getStatus());

        assertStatusAndFiles(DocumentStatus.IN_REVIEW, "storage://101", true);
        assertEquals(ApprovalRequestStatus.PENDING, requests.findById(original.getId()).orElseThrow().getStatus());
        assertTrue(logs.findByDocumentIdOrderByCreatedDateAsc(id).isEmpty());
        assertNotNull(writtenReference.get());
        assertFalse(storageMetadata.existsById(DocumentPdfReference.parseMetadataId(writtenReference.get())));
        try (var savedFiles = Files.list(storageDirectory)) {
            assertEquals(0, savedFiles.count());
        }
    }

    // Tạo tệp chính và yêu cầu đang chờ cho cùng một lượt.
    private DocumentApprovalRequest prepareReviewRound() {
        tx.executeWithoutResult(status -> attachments.saveAndFlush(DocumentAttachment.builder()
                .document(documents.getReferenceById(id)).uploadedByUser(author)
                .originalFileName("round.pdf").storageFileName("round.pdf").storagePath("round")
                .fileExtension("PDF").mimeType("application/pdf").fileSizeBytes(2).sha256Checksum("sample")
                .isMainDocument(true).build()));
        signs(dean);
        return request(id, dean, ApprovalRequestStatus.PENDING, false, time);
    }

    // Giữ khóa của giao dịch đầu cho tới khi giao dịch thứ hai bắt đầu xử lý.
    private List<MvcResult> race(String firstAction, String secondAction) throws Exception {
        var firstLocked = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var lockerPid = new AtomicInteger();
        boolean onPostgres = System.getenv("EOFFICE_TASK3_TEST_JDBC_URL") != null
                && !System.getenv("EOFFICE_TASK3_TEST_JDBC_URL").isBlank();
        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<MvcResult> first = workers.submit(() -> tx.execute(status -> {
                documents.findByIdForUpdate(id).orElseThrow();
                if (onPostgres) {
                    lockerPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                }
                firstLocked.countDown();
                awaitSignal(releaseFirst);
                try {
                    MvcResult result = applyReviewAction(firstAction);
                    if (result.getResponse().getStatus() >= 400) {
                        status.setRollbackOnly();
                    }
                    return result;
                } catch (Exception failure) {
                    throw new CompletionException(failure);
                }
            }));
            try {
                awaitSignal(firstLocked);
                Future<MvcResult> second = workers.submit(() -> {
                    secondStarted.countDown();
                    return applyReviewAction(secondAction);
                });
                awaitSignal(secondStarted);
                if (onPostgres) {
                    assertWaitingForPostgresLock(lockerPid.get());
                } else {
                    assertThrows(TimeoutException.class, () -> second.get(400, TimeUnit.MILLISECONDS));
                }
                releaseFirst.countDown();
                return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            } finally {
                releaseFirst.countDown();
            }
        }
    }

    // Xác nhận PostgreSQL thấy transaction thứ hai đang chờ đúng khóa của transaction đầu.
    private void assertWaitingForPostgresLock(int blockerPid) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) {
            Boolean blocked = jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM pg_stat_activity
                      WHERE datname = current_database() AND ? = ANY(pg_blocking_pids(pid)))
                    """, Boolean.class, blockerPid);
            if (Boolean.TRUE.equals(blocked)) {
                return;
            }
            Thread.sleep(25);
        }
        fail("PostgreSQL không ghi nhận giao dịch thứ hai chờ khóa văn bản");
    }

    // Đợi tín hiệu trong thời gian giới hạn để test không treo.
    private static void awaitSignal(CountDownLatch signal) {
        try {
            assertTrue(signal.await(8, TimeUnit.SECONDS), "Không nhận được tín hiệu giao dịch");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Bị ngắt khi đợi giao dịch", failure);
        }
    }

    // Gọi API bằng đúng tài khoản của thao tác cần kiểm thử.
    private MvcResult applyReviewAction(String action) throws Exception {
        if (action.equals("recall")) {
            return mvc.perform(post("/documents/" + id + "/recall-approval")
                    .with(authentication(authWithRole(author, Role.USER)))
                    .contentType("application/json").content("{\"recallReason\":\"recall\"}")).andReturn();
        }
        return mvc.perform(post("/documents/" + id + "/workflow/" + action)
                .with(authentication(auth(dean))).contentType("application/json")
                .content("{\"comment\":\"" + action + "\"}")).andReturn();
    }

    // Đối chiếu trạng thái và cờ tệp chính sau khi giao dịch commit.
    private void assertStatusAndFiles(DocumentStatus expected, String pdf, boolean activeMain) {
        tx.executeWithoutResult(status -> {
            Document committed = documents.findById(id).orElseThrow();
            assertEquals(expected, committed.getStatus());
            assertEquals(pdf, committed.getPrimaryFilePath());
            assertEquals(activeMain, !attachments.findByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(id).isEmpty());
        });
    }

    // Kiểm tra bàn giao chỉ sinh một yêu cầu mới và giữ hash của PDF ký nháy.
    private void assertHandover(DocumentApprovalRequest original) throws Exception {
        assertStatusAndFiles(DocumentStatus.PENDING_APPROVAL, "storage://102", true);
        var workflowLogs = logs.findByDocumentIdOrderByCreatedDateAsc(id);
        assertEquals(2, workflowLogs.size());
        String signedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[]{3, 4}));
        assertEquals(signedHash, workflowLogs.getFirst().getSignatureHash());
        assertEquals(signedHash, workflowLogs.getLast().getSignatureHash());
        var pending = requests.findByDocumentIdAndDeleteFlgFalseOrderBySubmittedAtDesc(id);
        assertEquals(2, pending.size());
        assertEquals(ApprovalRequestStatus.PENDING, pending.getFirst().getStatus());
        assertEquals(rector.getId(), pending.getFirst().getApproverUser().getId());
        assertEquals(original.getId(), pending.getLast().getId());
        assertEquals(ApprovalRequestStatus.APPROVED, pending.getLast().getStatus());
    }

}
