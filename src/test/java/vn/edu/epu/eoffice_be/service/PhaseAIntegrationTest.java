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
    @Autowired DocumentWorkflowService workflow;
    @Autowired DocumentBookService books;
    @Autowired PositionPermissionRepository positionPermissions;
    @Autowired PositionDegRepository delegations;
    @Autowired PermissionResolver permissionResolver;
    @Autowired DocumentProcessLogRepository processLogs;
    @Autowired DocumentWorkflowLogRepository workflowLogs;
    @Autowired PlatformTransactionManager manager;
    @MockBean DocumentPdfStorageService pdfStorage;
    @MockBean vn.edu.epu.eoffice_be.storage.FileStorageService legacyStorage;
    @MockBean FileStorageProviderRegistry registry;
    @MockBean PdfSigner signer;
    @MockBean DocumentNotificationEventListener notifications;
    @MockBean vn.edu.epu.eoffice_be.security.pki.PdfSignatureVerifier verifier;
    TransactionTemplate tx;
    UserLogin author, former, receiver, admin, clerk, rector, vice, outsider;
    OrgNode faculty, other, school;
    Long draftId, reviewId;
    @BeforeEach void setup(){
        tx=new TransactionTemplate(manager);
        tx.executeWithoutResult(status->{
            positions.deleteAll();positions.flush();
            faculty=unit("FACULTY");other=unit("DEPARTMENT");school=unit("UNIVERSITY");
            author=user(Role.USER);former=user(Role.REVIEWER);receiver=user(Role.REVIEWER);
            admin=user(Role.ADMIN);outsider=user(Role.USER);clerk=user(Role.CLERK);rector=user(Role.LEADER);vice=user(Role.LEADER);
            assign(author,faculty,"GV");assign(former,faculty,"TK");assign(receiver,faculty,"PK");
            assign(rector,school,"HT");assign(vice,school,"PHT");
            draftId=document(DocumentStatus.DRAFT,author).getId();
            var review=document(DocumentStatus.IN_REVIEW,author);reviewId=review.getId();
            request(review,author,former,ApprovalRequestStatus.APPROVED,false);
            request(review,author,receiver,ApprovalRequestStatus.PENDING,false);
            request(review,author,outsider,ApprovalRequestStatus.RECALLED,true);
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
        mvc.perform(get("/documents/"+reviewId).with(authentication(auth(outsider)))).andExpect(status().isForbidden());
        mvc.perform(get("/documents/"+draftId).with(authentication(auth(former)))).andExpect(status().isForbidden());
        verify(pdfStorage,never()).readAsResource(any());
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,()->versionService.restoreVersion(reviewId,1,former.getId())).getErrorCode());
    }
    @Test void forgedJwtAdminClaimDoesNotChangeUnitOrReadPermissions() throws Exception {
        mvc.perform(get("/documents/draft-options").with(authentication(auth(author,"SYSTEM_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.adminSimulationEnabled").value(false))
                .andExpect(jsonPath("$.data.issuingOrgs.length()").value(1))
                .andExpect(jsonPath("$.data.issuingOrgs[0].id").value(faculty.getId()));
        mvc.perform(get("/documents/"+draftId).with(authentication(auth(outsider,"ADMIN")))).andExpect(status().isForbidden());
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
        SecurityContextHolder.getContext().setAuthentication(auth(admin));
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,()->publish.publishDocument(response.getId(),new PublishRequest())).getErrorCode());
        verify(pdfStorage,never()).readPdf(any());
    }
    @Test void publishedDetailsKeepTheirOldBranchWhileDraftListsAreFiltered() throws Exception {
        Long published=tx.execute(s->document(DocumentStatus.PUBLISHED,author).getId());
        mvc.perform(get("/documents/"+published).with(authentication(auth(admin)))).andExpect(status().isOk());
        mvc.perform(get("/documents/drafts").param("status","DRAFT").with(authentication(auth(outsider))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/documents/drafts").param("status","IN_REVIEW").with(authentication(auth(former))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
    }
    @Test void rawProviderEndpointCannotDownloadOrListAnotherDraftFile() throws Exception {
        FileStorageProvider provider=mock(FileStorageProvider.class);
        when(registry.getActiveProvider()).thenReturn(provider);when(provider.getName()).thenReturn("synthetic");
        tx.executeWithoutResult(s->metadata.saveAndFlush(StoredFileMetadata.builder().provider("synthetic").fileId("protected")
                .originalFileName("draft.pdf").metadata(Map.of("documentId",draftId.toString())).build()));
        mvc.perform(get("/file-storage/protected/download").with(authentication(auth(outsider)))).andExpect(status().isForbidden());
        verify(provider,never()).get(any());
        when(provider.getAll()).thenReturn(List.of(StoredFile.builder().id("protected").originalFileName("draft.pdf").build()));
        mvc.perform(get("/file-storage").with(authentication(auth(outsider)))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
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
            mvc.perform(get("/documents/"+reviewId+suffix).with(authentication(auth(outsider)))).andExpect(status().isForbidden());
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
        var unassignedReviewer=tx.execute(status -> user(Role.REVIEWER));
        mvc.perform(get("/documents/pending-review").with(authentication(auth(unassignedReviewer))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/documents/pending-review").with(authentication(auth(former))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        tx.executeWithoutResult(s -> { var user = users.findById(former.getId()).orElseThrow(); user.setUserStatusCd("DISABLED"); users.saveAndFlush(user); });
        mvc.perform(get("/documents/pending-review").with(authentication(auth(former))))
                .andExpect(status().isForbidden());
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> administrativeStates() {
        return java.util.stream.Stream.of(Role.ADMIN,Role.SYSTEM_ADMIN).flatMap(role ->
                java.util.Arrays.stream(DocumentStatus.values()).map(state -> org.junit.jupiter.params.provider.Arguments.of(role,state)));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("administrativeStates")
    void adminReadsEveryStateWithoutParticipatingAndDeletedDocumentsStayHidden(Role role,DocumentStatus state) throws Exception {
        tx.executeWithoutResult(s -> {
            var account=users.findById(admin.getId()).orElseThrow();account.setRole(role);users.saveAndFlush(account);
            var doc=documents.findByIdAndDeleteFlgFalse(draftId).orElseThrow();doc.setStatus(state);
            doc.setTitle("Admin visibility probe");doc.setOfficialFilePath("storage://502");documents.saveAndFlush(doc);
        });
        when(pdfStorage.readAsResource("storage://501")).thenReturn(new ByteArrayResource(new byte[]{1,2}));
        when(pdfStorage.readAsResource("storage://502")).thenReturn(new ByteArrayResource(new byte[]{3,4}));
        when(pdfStorage.readPdf(any())).thenReturn(new byte[]{1,2});
        when(verifier.verify(any(byte[].class))).thenReturn(vn.edu.epu.eoffice_be.dto.response.DocumentSignatureVerifyResponse.builder().overallStatus("VALID").build());
        var identity=auth(admin,"USER");
        for(String suffix:List.of("","/details","/download","/versions","/workflow-logs","/timeline","/files/primary","/files/official","/signatures/verify")) {
            mvc.perform(get("/documents/"+draftId+suffix).with(authentication(identity))).andExpect(status().isOk());
        }
        for(String queue:List.of("pending-review","pending-sign","pending-publish"))
            mvc.perform(get("/documents/"+queue).with(authentication(identity))).andExpect(status().isOk());
        mvc.perform(get("/documents/drafts").param("keyword","Admin visibility probe").param("orgNodeId",faculty.getId().toString())
                .param("status",state.name()).param("size","1").with(authentication(identity)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(draftId));
        tx.executeWithoutResult(s -> {var doc=documents.findById(draftId).orElseThrow();doc.setDeleteFlg(true);documents.saveAndFlush(doc);});
        mvc.perform(get("/documents/"+draftId).with(authentication(identity))).andExpect(status().isNotFound());
        mvc.perform(get("/documents/drafts").param("keyword","Admin visibility probe").param("orgNodeId",faculty.getId().toString())
                .with(authentication(identity))).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        verifyNoInteractions(signer);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value=Role.class,names={"ADMIN","SYSTEM_ADMIN"})
    void adminCanSignAndSubmitOwnDraftButCannotApproveItOrPublish(Role role) {
        tx.executeWithoutResult(s -> {var account=users.findById(admin.getId()).orElseThrow();account.setRole(role);users.saveAndFlush(account);});
        var response=drafts.createDraft(create(faculty.getId()),admin.getId());
        tx.executeWithoutResult(s -> {var doc=documents.findById(response.getId()).orElseThrow();doc.setPrimaryFilePath("storage://501");documents.saveAndFlush(doc);});
        when(pdfStorage.readPdf("storage://501")).thenReturn(new byte[]{1,2});
        when(signer.signBytes(any(),eq(admin.getId().toString()),any(),any(),any(),any())).thenReturn(
                new PdfSigner.SignResult(admin.getId().toString(),"author","serial","hash",new Date(),"field",null,new byte[]{3,4}));
        when(pdfStorage.storeSignedPdf(any(),any(),eq(response.getId()))).thenReturn("storage://503");
        SecurityContextHolder.getContext().setAuthentication(auth(admin));
        var sent=approval.submitForApproval(response.getId(),SubmitApprovalRequest.builder().route(SubmissionRoute.REVIEW)
                .approverUserId(former.getId()).build(),admin.getId());
        assertEquals(DocumentStatus.IN_REVIEW,sent.getStatus());
        assertTrue(sent.isTestDocument());
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,() -> workflow.approve(response.getId(),DocumentApproveRequest.builder().build())).getErrorCode());
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,() -> publish.publishDocument(response.getId(),new PublishRequest())).getErrorCode());
        verify(signer,times(1)).signBytes(any(),eq(admin.getId().toString()),any(),any(),any(),any());
        var recalled=approval.recallApproval(response.getId(),null,admin.getId());
        assertEquals(DocumentStatus.DRAFT,recalled.getStatus());
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> administrativeGrants() {
        return java.util.stream.Stream.of(Role.ADMIN,Role.SYSTEM_ADMIN).flatMap(role ->
                java.util.stream.Stream.of("POSITION","DELEGATION").map(grant -> org.junit.jupiter.params.provider.Arguments.of(role,grant)));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("administrativeGrants")
    void adminCannotProcessAnotherDocumentEvenWithRealBusinessPermission(Role role,String grant) throws Exception {
        tx.executeWithoutResult(s -> {
            var account=users.findById(admin.getId()).orElseThrow();account.setRole(role);users.saveAndFlush(account);
            var permission=positionPermissions.findByPositionCdAndPermissionCdAndDeleteFlgFalse("TK","DOCUMENT_APPROVE")
                    .orElseGet(() -> PositionPermission.builder().positionCd("TK").permissionCd("DOCUMENT_APPROVE").build());
            permission.setInheritToChildren(false); // Exact-unit control; inheritance SQL is outside this test.
            positionPermissions.saveAndFlush(permission);
            if(grant.equals("POSITION")) assign(admin,faculty,"TK");
            else delegations.saveAndFlush(PositionDeg.builder().orgNode(faculty).permissionCd("DOCUMENT_APPROVE")
                    .delegator(former).delegatee(admin).effectiveFrom(Instant.now().minusSeconds(60)).effectiveTo(Instant.now().plusSeconds(3600)).build());
        });
        assertTrue(permissionResolver.hasPermission(admin.getId(),role,"DOCUMENT_APPROVE",faculty.getId()),"Control: the position/delegation really grants permission");
        SecurityContextHolder.getContext().setAuthentication(auth(admin));
        long approvalCount=requests.count(),versionCount=versions.count(),logCount=processLogs.count(),workflowCount=workflowLogs.count();
        java.util.List<org.junit.jupiter.api.function.Executable> forbidden=List.of(
                () -> workflow.approve(reviewId,DocumentApproveRequest.builder().build()),
                () -> workflow.initialSign(reviewId,DocumentInitialSignRequest.builder().build()),
                () -> workflow.requestRevision(reviewId,DocumentRevisionRequest.builder().comment("Revision").build()),
                () -> workflow.reject(reviewId,DocumentRejectRequest.builder().comment("Reject").build()),
                () -> workflow.revokeDocument(reviewId,DocumentRevokeRequest.builder().comment("Revoke").build()),
                () -> workflow.signAndPublish(reviewId,DocumentPublishRequest.builder().build()),
                () -> publish.publishDocument(reviewId,new PublishRequest()),
                () -> books.assignNumber(new AssignDocumentNumberRequest()),
                () -> approval.recallApproval(reviewId,null,admin.getId()));
        for(var action:forbidden) assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,action).getErrorCode());
        mvc.perform(post("/documents/"+reviewId+"/workflow/approve").contentType("application/json").content("{}")
                .with(authentication(auth(admin)))).andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        mvc.perform(post("/documents/"+reviewId+"/publish").contentType("application/json").content("{}")
                .with(authentication(auth(admin)))).andExpect(status().isForbidden());
        assertEquals(DocumentStatus.IN_REVIEW,documents.findById(reviewId).orElseThrow().getStatus());
        assertEquals(approvalCount,requests.count());assertEquals(versionCount,versions.count());
        assertEquals(logCount,processLogs.count());assertEquals(workflowCount,workflowLogs.count());
        verifyNoInteractions(signer,pdfStorage,notifications);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value=Role.class,names={"ADMIN","SYSTEM_ADMIN"})
    void adminReadBypassDoesNotAuthorizeRawFileMutationInPublishedState(Role role) throws Exception {
        String fileId="published-protected-"+UUID.randomUUID();
        tx.executeWithoutResult(s -> {var account=users.findById(admin.getId()).orElseThrow();account.setRole(role);users.saveAndFlush(account);});
        tx.executeWithoutResult(s -> {var doc=documents.findById(draftId).orElseThrow();doc.setStatus(DocumentStatus.PUBLISHED);documents.saveAndFlush(doc);
            metadata.saveAndFlush(StoredFileMetadata.builder().provider("synthetic").fileId(fileId)
                    .originalFileName("published.pdf").metadata(Map.of("documentId",draftId.toString())).build());});
        var provider=mock(FileStorageProvider.class);when(provider.getName()).thenReturn("synthetic");when(registry.getActiveProvider()).thenReturn(provider);
        when(provider.get(fileId)).thenReturn(StoredFile.builder().id(fileId)
                .originalFileName("published.pdf").content(new byte[]{1,2}).build());
        mvc.perform(get("/file-storage/"+fileId+"/download").with(authentication(auth(admin)))).andExpect(status().isOk());
        mvc.perform(delete("/file-storage/"+fileId).with(authentication(auth(admin)))).andExpect(status().isForbidden());
        verify(provider,never()).delete(any());
    }
    @Test void disabledOrDeletedAdminDoesNotKeepReadAllAndClaimedAdminDoesNotGrantIt() throws Exception {
        mvc.perform(get("/documents/"+draftId).with(authentication(auth(outsider,"ADMIN")))).andExpect(status().isForbidden());
        mvc.perform(get("/documents/drafts").param("status","DRAFT").with(authentication(auth(outsider,"ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        for(String queue:List.of("pending-review","pending-sign","pending-publish"))
            mvc.perform(get("/documents/"+queue).with(authentication(auth(outsider,"ADMIN")))).andExpect(status().isForbidden());
        tx.executeWithoutResult(s -> {var account=users.findById(admin.getId()).orElseThrow();account.setUserStatusCd("DISABLED");users.saveAndFlush(account);});
        mvc.perform(get("/documents/"+draftId).with(authentication(auth(admin)))).andExpect(status().isForbidden());
        tx.executeWithoutResult(s -> {var account=users.findById(admin.getId()).orElseThrow();account.setDeleteFlg(true);users.saveAndFlush(account);});
        mvc.perform(get("/documents/"+draftId).with(authentication(auth(admin)))).andExpect(status().isUnauthorized());
        verifyNoInteractions(pdfStorage,signer);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value=Role.class,names={"ADMIN","SYSTEM_ADMIN"})
    void adminSearchKeepsPaginationCountsOrderingAndExplicitFiltersAcrossUnits(Role role) throws Exception {
        String keyword="admin-paging-"+UUID.randomUUID();
        var ids=tx.execute(s -> {
            var account=users.findById(admin.getId()).orElseThrow();account.setRole(role);users.saveAndFlush(account);
            List<Long> created=new ArrayList<>();
            var states=List.of(DocumentStatus.DRAFT,DocumentStatus.REJECTED,DocumentStatus.PUBLISHED);
            for(int i=0;i<states.size();i++) {
                var doc=document(states.get(i),author);doc.setTitle(keyword+"-"+i);
                if(i==1) {doc.setIssuingOrgNode(other);doc.setIssuingOrg(other);}
                created.add(documents.saveAndFlush(doc).getId());
            }
            return created;
        });
        List<Long> returned=new ArrayList<>();
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        for(int page=0;page<2;page++) {
            var result=mvc.perform(get("/documents/drafts").param("keyword",keyword).param("size","2").param("page",Integer.toString(page))
                    .with(authentication(auth(admin,"USER")))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(3)).andExpect(jsonPath("$.data.totalPages").value(2))
                    .andExpect(jsonPath("$.data.content.length()").value(page==0?2:1)).andReturn();
            for(var row:mapper.readTree(result.getResponse().getContentAsString()).path("data").path("content")) returned.add(row.path("id").asLong());
        }
        assertEquals(new HashSet<>(ids),new HashSet<>(returned));assertEquals(3,returned.size());
        Instant previous=null;
        for(Long id:returned) {
            Instant created=documents.findById(id).orElseThrow().getCreatedDate();assertNotNull(created);
            if(previous!=null) assertFalse(created.isAfter(previous));previous=created;
        }
        mvc.perform(get("/documents/drafts").param("keyword",keyword).param("orgNodeId",other.getId().toString())
                .param("status","REJECTED").with(authentication(auth(admin))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(ids.get(1)));
    }

}
