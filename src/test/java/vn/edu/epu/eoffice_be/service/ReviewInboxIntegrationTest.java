package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.security.pki.PdfSigner;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Isolated H2, real security/service/repository, no outer transaction or real cryptography. */
@SpringBootTest(properties="spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ReviewInboxIntegrationTest {
    static final String DB="jdbc:h2:mem:review-inbox-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH";
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){registry.add("spring.datasource.url",()->DB);}
    @Autowired UserLoginRepository users;
    @Autowired OrgNodeRepository orgs;
    @Autowired PositionAssignmentRepository positions;
    @Autowired PositionPermissionRepository permissions;
    @Autowired PositionDegRepository delegations;
    @Autowired PermissionResolver resolver;
    @Autowired DocumentRepository documents;
    @Autowired DocumentApprovalRequestRepository requests;
    @Autowired DocumentWorkflowLogRepository logs;
    @Autowired PlatformTransactionManager manager;
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

}
