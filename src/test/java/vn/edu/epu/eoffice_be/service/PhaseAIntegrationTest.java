package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.core.io.ByteArrayResource;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.PublishRequest;
import vn.edu.epu.eoffice_be.dto.request.*;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.security.pki.PdfSigner;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;
import vn.edu.epu.eoffice_be.storage.provider.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/** Real DB/service/security; isolated H2 UUID, no OSIV or outer transaction, no real PKI/storage. */
@SpringBootTest(properties={"spring.jpa.open-in-view=false","eoffice.workflow.admin-simulation-enabled=true"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PhaseAIntegrationTest {
    static final String DB="jdbc:h2:mem:phase-a-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH";
    @DynamicPropertySource static void db(DynamicPropertyRegistry registry){registry.add("spring.datasource.url",()->DB);}
    @Autowired UserLoginRepository users;
    @Autowired OrgNodeRepository orgs;
    @Autowired PositionAssignmentRepository positions;
    @Autowired DocumentRepository documents;
    @Autowired DocumentApprovalRequestRepository requests;
    @Autowired StoredFileMetadataRepository metadata;
    @Autowired DocumentAttachmentRepository attachments;
    @Autowired DocumentVersionRepository versions;
    @Autowired DocumentDraftService drafts;
    @Autowired DocumentVersionService versionService;
    @Autowired DocumentAttachmentService attachmentService;
    @Autowired DocumentReadPolicy policy;
    @Autowired DocumentPublishService publish;
    @Autowired DocumentApprovalService approval;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager manager;
    @MockBean DocumentPdfStorageService pdfStorage;
    @MockBean vn.edu.epu.eoffice_be.storage.FileStorageService legacyStorage;
    @MockBean FileStorageProviderRegistry registry;
    @MockBean PdfSigner signer;
    TransactionTemplate tx;
    UserLogin author, former, receiver, admin, clerk, rector, vice;
    OrgNode faculty, other, school;
    Long draftId, reviewId;
    @BeforeEach void setup(){
        tx=new TransactionTemplate(manager);
        tx.executeWithoutResult(status->{
            positions.deleteAll();positions.flush();
            faculty=unit("FACULTY");other=unit("DEPARTMENT");school=unit("UNIVERSITY");
            author=user(Role.USER);former=user(Role.REVIEWER);receiver=user(Role.REVIEWER);
            admin=user(Role.ADMIN);clerk=user(Role.CLERK);rector=user(Role.LEADER);vice=user(Role.LEADER);
            assign(author,faculty,"GV");assign(former,faculty,"TK");assign(receiver,faculty,"PK");
            assign(rector,school,"HT");assign(vice,school,"PHT");
            draftId=document(DocumentStatus.DRAFT,author).getId();
            var review=document(DocumentStatus.IN_REVIEW,author);reviewId=review.getId();
            request(review,author,former,ApprovalRequestStatus.APPROVED,false);
            request(review,author,receiver,ApprovalRequestStatus.PENDING,false);
            request(review,author,admin,ApprovalRequestStatus.RECALLED,true);
            versions.saveAndFlush(DocumentVersion.builder().document(review).versionNumber(1).title("Version title").summary("Version summary").createdByUser(author).build());
        });
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    OrgNode unit(String type){return orgs.saveAndFlush(OrgNode.builder().code("A-"+UUID.randomUUID()).nameVn(type).nameEn(type).typeCd(type).build());}
    UserLogin user(Role role){return users.saveAndFlush(UserLogin.builder().username("a-"+UUID.randomUUID()).passwordHash("synthetic").role(role).build());}
    void assign(UserLogin user,OrgNode org,String cd){positions.saveAndFlush(PositionAssignment.builder().userLogin(user).orgNode(org).positionCd(cd).effectiveFrom(Instant.now().minusSeconds(60)).build());}
    Document document(DocumentStatus status,UserLogin drafter){return documents.saveAndFlush(Document.builder().title("Draft title").summary("Summary").content("Content").documentTypeCd("CONG_VAN").drafter(drafter).issuingOrgNode(faculty).issuingOrg(faculty).primaryFilePath("storage://501").status(status).build());}
    void request(Document doc,UserLogin sender,UserLogin approver,ApprovalRequestStatus status,boolean deleted){requests.saveAndFlush(DocumentApprovalRequest.builder().document(doc).senderUser(sender).approverUser(approver).status(status).deleteFlg(deleted).submittedAt(Instant.now()).build());}
    UsernamePasswordAuthenticationToken auth(UserLogin user){return auth(user,user.getRole().name());}
    UsernamePasswordAuthenticationToken auth(UserLogin user,String claim){return new UsernamePasswordAuthenticationToken(new JwtUserPrincipal(user.getId(),user.getUsername(),claim,true,List.of()),null,List.of(new SimpleGrantedAuthority("ROLE_"+claim)));}
    DocumentCreateRequest create(Long org){return DocumentCreateRequest.builder().title("New draft").summary("Summary").content("Content").documentTypeCd("CONG_VAN").issuingOrgNodeId(org).build();}

    @Test void historicalAndCurrentPeopleReadButSoftDeletedRequestAndRoleDoNotGrantAccess() throws Exception {
        mvc.perform(get("/documents/"+reviewId).with(authentication(auth(former)))).andExpect(status().isOk());
        mvc.perform(get("/documents/"+reviewId+"/versions").with(authentication(auth(former)))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/documents/"+reviewId).with(authentication(auth(receiver)))).andExpect(status().isOk());
        mvc.perform(get("/documents/"+reviewId).with(authentication(auth(admin)))).andExpect(status().isForbidden());
        mvc.perform(get("/documents/"+draftId).with(authentication(auth(former)))).andExpect(status().isForbidden());
        verify(pdfStorage,never()).readAsResource(any());
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,()->versionService.restoreVersion(reviewId,1,former.getId())).getErrorCode());
    }
    @Test void forgedJwtAdminClaimDoesNotChangeUnitOrReadPermissions() throws Exception {
        mvc.perform(get("/documents/draft-options").with(authentication(auth(author,"SYSTEM_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.adminSimulationEnabled").value(false))
                .andExpect(jsonPath("$.data.issuingOrgs.length()").value(1))
                .andExpect(jsonPath("$.data.issuingOrgs[0].id").value(faculty.getId()));
        mvc.perform(get("/documents/"+draftId).with(authentication(auth(admin)))).andExpect(status().isForbidden());
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,()->drafts.createDraft(create(other.getId()),author.getId())).getErrorCode());
    }
    @Test void nullableDirectUnitAndSchoolLeaderMetadataArePersistedWithoutFallback() {
        var clerkDraft=drafts.createDraft(create(null),clerk.getId());
        assertNull(clerkDraft.getIssuingOrgNodeId());
        var stored=documents.findByIdAndDeleteFlgFalse(clerkDraft.getId()).orElseThrow();
        assertNull(stored.getIssuingOrgNode());assertNull(stored.getIssuingOrg());
        var leaderDraft=drafts.createDraft(create(null),vice.getId());assertEquals(school.getId(),leaderDraft.getIssuingOrgNodeId());
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,()->drafts.createDraft(create(faculty.getId()),vice.getId())).getErrorCode());
    }
    @Test void simulationIsMarkedAndCannotEnterRealPublication() {
        var response=drafts.createDraft(create(other.getId()),admin.getId());assertTrue(response.isTestDocument());
        var stored=documents.findByIdAndDeleteFlgFalse(response.getId()).orElseThrow();assertTrue(stored.isTestDocument());
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,()->publish.publishDocument(response.getId(),new PublishRequest())).getErrorCode());
        verify(pdfStorage,never()).readPdf(any());
    }
    @Test void publishedDetailsKeepTheirOldBranchWhileDraftListsAreFiltered() throws Exception {
        Long published=tx.execute(s->document(DocumentStatus.PUBLISHED,author).getId());
        mvc.perform(get("/documents/"+published).with(authentication(auth(admin)))).andExpect(status().isOk());
        mvc.perform(get("/documents/drafts").param("status","DRAFT").with(authentication(auth(admin))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/documents/drafts").param("status","IN_REVIEW").with(authentication(auth(former))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
    }
    @Test void rawProviderEndpointCannotDownloadOrListAnotherDraftFile() throws Exception {
        FileStorageProvider provider=mock(FileStorageProvider.class);
        when(registry.getActiveProvider()).thenReturn(provider);when(provider.getName()).thenReturn("synthetic");
        tx.executeWithoutResult(s->metadata.saveAndFlush(StoredFileMetadata.builder().provider("synthetic").fileId("protected")
                .originalFileName("draft.pdf").metadata(Map.of("documentId",draftId.toString())).build()));
        mvc.perform(get("/file-storage/protected/download").with(authentication(auth(admin)))).andExpect(status().isForbidden());
        verify(provider,never()).get(any());
        when(provider.getAll()).thenReturn(List.of(StoredFile.builder().id("protected").originalFileName("draft.pdf").build()));
        mvc.perform(get("/file-storage").with(authentication(auth(admin)))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }
    @Test void oldRequestSenderStillReadsWhenDrafterOrStateChanges() {
        tx.executeWithoutResult(s->{var doc=documents.findByIdAndDeleteFlgFalse(reviewId).orElseThrow();doc.setDrafter(clerk);doc.setStatus(DocumentStatus.DRAFT);documents.saveAndFlush(doc);});
        var doc=documents.findByIdAndDeleteFlgFalse(reviewId).orElseThrow();
        assertDoesNotThrow(()->policy.assertRead(doc,author.getId()));
        assertDoesNotThrow(()->policy.assertRead(doc,former.getId()));
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,()->policy.assertDraftWrite(doc,former.getId())).getErrorCode());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(ApprovalRequestStatus.class)
    void everyUndeletedRequestStatusGrantsHistoricalReadOnly(ApprovalRequestStatus status) {
        tx.executeWithoutResult(s -> {
            var doc = documents.findByIdAndDeleteFlgFalse(draftId).orElseThrow();
            request(doc, author, former, status, false);
        });
        var doc = documents.findByIdAndDeleteFlgFalse(draftId).orElseThrow();
        assertDoesNotThrow(() -> policy.assertRead(doc, former.getId()));
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class,
                () -> policy.assertDraftWrite(doc, former.getId())).getErrorCode());
    }
    @Test void participantRightsDoNotCrossDocumentsAndSoftDeleteRevokesThem() {
        var doc = documents.findByIdAndDeleteFlgFalse(draftId).orElseThrow();
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class,
                () -> policy.assertRead(doc, former.getId())).getErrorCode());
        tx.executeWithoutResult(s -> request(doc, author, former, ApprovalRequestStatus.APPROVED, true));
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class,
                () -> policy.assertRead(doc, former.getId())).getErrorCode());
    }
    @Test void attachmentPdfAndVersionEndpointsCannotBypassTheDocumentPolicy() throws Exception {
        Long attachmentId = tx.execute(s -> attachments.saveAndFlush(DocumentAttachment.builder()
                .document(documents.findByIdAndDeleteFlgFalse(reviewId).orElseThrow())
                .originalFileName("draft.pdf").storageFileName("draft.pdf").storagePath("isolated")
                .fileExtension("PDF").mimeType("application/pdf").fileSizeBytes(12)
                .sha256Checksum("a".repeat(64)).uploadedByUser(author).isMainDocument(true).build()).getId());
        when(pdfStorage.readAsResource("storage://501")).thenReturn(new ByteArrayResource(new byte[]{1,2}));
        when(legacyStorage.loadAsResource("isolated","draft.pdf")).thenReturn(new ByteArrayResource(new byte[]{1,2}));
        for (String suffix : List.of("/files/primary","/attachments/"+attachmentId+"/download","/versions/compare?fromVersion=1&toVersion=1")) {
            mvc.perform(get("/documents/"+reviewId+suffix).with(authentication(auth(former)))).andExpect(status().isOk());
            mvc.perform(get("/documents/"+reviewId+suffix).with(authentication(auth(admin)))).andExpect(status().isForbidden());
        }
        assertEquals(ErrorCode.DOCUMENT_ATTACHMENT_NOT_FOUND, assertThrows(BusinessException.class,
                () -> attachmentService.getAttachmentMetadata(draftId,attachmentId,author.getId())).getErrorCode());
        verify(pdfStorage,times(1)).readAsResource(any());
        verify(legacyStorage,times(1)).loadAsResource(any(),any());
        var file = new org.springframework.mock.web.MockMultipartFile("file","draft.pdf","application/pdf",new byte[]{1,2});
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,
                () -> attachmentService.uploadAttachment(draftId,file,true,admin.getId())).getErrorCode());
        verify(legacyStorage,never()).storeFile(any(),any());
    }
    @Test void updatesCannotChangeUnitOrClearSimulationMarker() {
        var update = DocumentUpdateRequest.builder().title("Updated").summary("Summary").content("Content")
                .documentTypeCd("CONG_VAN").issuingOrgNodeId(other.getId()).build();
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,
                () -> drafts.updateDraft(draftId,update,author.getId())).getErrorCode());
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,
                () -> drafts.updateDraft(draftId,update,admin.getId())).getErrorCode());
        assertEquals("Draft title",documents.findByIdAndDeleteFlgFalse(draftId).orElseThrow().getTitle());
        var test = drafts.createDraft(create(other.getId()),admin.getId());
        update.setIssuingOrgNodeId(null);
        var changed = drafts.updateDraft(test.getId(),update,admin.getId());
        assertTrue(changed.isTestDocument()); assertNull(changed.getIssuingOrgNodeId());
        var stored = documents.findByIdAndDeleteFlgFalse(test.getId()).orElseThrow();
        assertTrue(stored.isTestDocument()); assertNull(stored.getIssuingOrgNode()); assertNull(stored.getIssuingOrg());
    }
    @Test void pendingListUsesHistoricalPolicyAndDatabaseAccountStatus() throws Exception {
        mvc.perform(get("/documents/pending-review").with(authentication(auth(admin))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/documents/pending-review").with(authentication(auth(former))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        tx.executeWithoutResult(s -> { var user = users.findById(former.getId()).orElseThrow(); user.setUserStatusCd("DISABLED"); users.saveAndFlush(user); });
        mvc.perform(get("/documents/pending-review").with(authentication(auth(former))))
                .andExpect(status().isForbidden());
    }

}
