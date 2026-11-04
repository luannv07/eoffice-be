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
    Long id;
    final Instant time=Instant.parse("2026-11-01T00:00:00Z");
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
    UsernamePasswordAuthenticationToken auth(UserLogin user){return new UsernamePasswordAuthenticationToken(
            new JwtUserPrincipal(user.getId(),user.getUsername(),"REVIEWER",true,List.of()),null,List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));}
    void signs(UserLogin user){
        when(pdfStorage.readPdf("storage://101")).thenReturn(new byte[]{1,2});
        when(signer.signBytes(any(),eq(user.getId().toString()),anyString(),anyString())).thenReturn(
                new PdfSigner.SignResult(user.getId().toString(),"reviewer","serial","hash",new Date(),"field",null,new byte[]{3,4}));
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
    @ParameterizedTest @CsvSource({"false,initial-sign,IN_REVIEW","true,initial-sign,IN_REVIEW",
            "false,approve,PENDING_APPROVAL","true,approve,PENDING_APPROVAL",
            "false,request-revision,NEEDS_REVISION","true,request-revision,NEEDS_REVISION",
            "false,reject,REJECTED","true,reject,REJECTED"})
    void onlySelectedDeanOrViceCanProcess(boolean selectVice,String action,DocumentStatus expected) throws Exception {
        UserLogin selected=selectVice?vice:dean;
        request(id,selected,ApprovalRequestStatus.PENDING,false,time);
        if(action.equals("initial-sign")||action.equals("approve")) signs(selected);
        mvc.perform(post("/documents/"+id+"/workflow/"+action).with(authentication(auth(selected)))
                .contentType("application/json").content("{\"comment\":\"review\"}"))
                .andExpect(status().isOk());
        assertEquals(expected,documents.findById(id).orElseThrow().getStatus());
        assertEquals(1,requests.findByDocumentIdAndDeleteFlgFalseOrderBySubmittedAtDesc(id).size());
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

}
