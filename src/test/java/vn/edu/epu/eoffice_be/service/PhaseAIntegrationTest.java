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
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
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
        tx.executeWithoutResult(s -> attachments.saveAndFlush(DocumentAttachment.builder()
                .document(documents.getReferenceById(response.getId())).uploadedByUser(users.getReferenceById(admin.getId()))
                .originalFileName("admin.pdf").storagePath("source").storageFileName("admin.pdf")
                .fileExtension("PDF").mimeType("application/pdf").sha256Checksum("hash").fileSizeBytes(2)
                .isMainDocument(true).build()));
        when(legacyStorage.loadAsResource("source", "admin.pdf")).thenReturn(new ByteArrayResource(new byte[]{1,2}));
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


    private Long uploadRoundPdf(Long id, boolean main, byte[] bytes) {
        String storageName = "round-" + UUID.randomUUID() + ".pdf";
        var file = new org.springframework.mock.web.MockMultipartFile("file", "same-name.pdf", "application/pdf", bytes);
        when(legacyStorage.storeFile(file, id)).thenReturn(vn.edu.epu.eoffice_be.storage.dto.StoredFileInfo.builder()
                .originalFileName("same-name.pdf").storageFileName(storageName).storagePath("round-source")
                .fileExtension("PDF").mimeType("application/pdf").fileSizeBytes(bytes.length).sha256Checksum("sample-hash").build());
        when(legacyStorage.loadAsResource("round-source", storageName)).thenReturn(new ByteArrayResource(bytes));
        return attachmentService.uploadAttachment(id, file, main, author.getId()).getId();
    }

    private void roundSigning() {
        when(signer.signBytes(any(), eq(author.getId().toString()), any(), any(), any(), any())).thenAnswer(inv ->
                new PdfSigner.SignResult(author.getId().toString(), "author", "serial", "hash", new Date(), "field", null,
                        ((byte[]) inv.getArgument(0)).clone()));
        when(pdfStorage.storeSignedPdf(any(), any(), eq(draftId))).thenAnswer(inv -> "storage://" + Math.abs(UUID.randomUUID().getMostSignificantBits()));
    }

    private void submitRound() {
        approval.submitForApproval(draftId, SubmitApprovalRequest.builder().approverUserId(former.getId()).build(), author.getId());
    }

    private void returnRound(boolean revision) {
        if (!revision) {
            approval.recallApproval(draftId, RecallApprovalRequest.builder().recallReason("Replace PDF").build(), author.getId());
        } else {
            tx.executeWithoutResult(s -> {
                var permission = positionPermissions.findByPositionCdAndPermissionCdAndDeleteFlgFalse("TK", "DOCUMENT_APPROVE")
                        .orElseGet(() -> PositionPermission.builder().positionCd("TK").permissionCd("DOCUMENT_APPROVE").build());
                permission.setInheritToChildren(false);
                positionPermissions.saveAndFlush(permission);
            });
            SecurityContextHolder.getContext().setAuthentication(auth(former));
            workflow.requestRevision(draftId, DocumentRevisionRequest.builder().comment("Replace PDF").build());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void returnedMainIsSoftDeletedAndOnlyNewMainIsSigned(boolean revision) {
        byte[] v1 = {1, 1}, v2 = {2, 2}, v3 = {3, 3};
        Long oldId = uploadRoundPdf(draftId, true, v1);
        Long appendix = uploadRoundPdf(draftId, false, new byte[]{9});
        roundSigning(); submitRound();
        returnRound(revision);
        assertEquals(Boolean.TRUE, jdbc.queryForObject("select delete_flg from document_attachments where id=?", Boolean.class, oldId));
        assertEquals(1, jdbc.queryForObject("select count(*) from document_attachments where id=?", Integer.class, oldId));
        assertEquals(Boolean.FALSE, jdbc.queryForObject("select delete_flg from document_attachments where id=?", Boolean.class, appendix));
        assertTrue(attachments.findByIdAndDocumentIdAndDeleteFlgFalse(oldId, draftId).isEmpty());
        assertNull(documents.findById(draftId).orElseThrow().getPrimaryFilePath());
        clearInvocations(signer, legacyStorage, pdfStorage);
        assertEquals(ErrorCode.DOCUMENT_PRIMARY_FILE_MISSING, assertThrows(BusinessException.class, this::submitRound).getErrorCode());
        uploadRoundPdf(draftId, false, new byte[]{8});
        assertThrows(BusinessException.class, this::submitRound);
        verifyNoInteractions(signer, pdfStorage);
        Long secondId = uploadRoundPdf(draftId, true, v2);
        submitRound();
        verify(signer).signBytes(eq(v2), eq(author.getId().toString()), any(), any(), any(), any());
        verify(signer, never()).signBytes(eq(v1), any(), any(), any(), any(), any());
        verify(pdfStorage, never()).readPdf(any());
        assertEquals(DocumentStatus.IN_REVIEW, documents.findById(draftId).orElseThrow().getStatus());
        returnRound(revision);
        assertEquals(Boolean.TRUE, jdbc.queryForObject("select delete_flg from document_attachments where id=?", Boolean.class, secondId));
        uploadRoundPdf(draftId, true, v3);
        clearInvocations(signer);
        submitRound();
        verify(signer).signBytes(eq(v3), eq(author.getId().toString()), any(), any(), any(), any());
        verify(legacyStorage, never()).deleteFile(any(), any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void returningAndRetiringMainRollBackTogether(boolean revision) {
        Long oldId = uploadRoundPdf(draftId, true, new byte[]{1});
        roundSigning(); submitRound();
        String before = documents.findById(draftId).orElseThrow().getPrimaryFilePath();
        // Prepare permissions outside the rollback transaction; then returnRound uses them.
        if (revision) tx.executeWithoutResult(s -> {
            var p = positionPermissions.findByPositionCdAndPermissionCdAndDeleteFlgFalse("TK", "DOCUMENT_APPROVE")
                    .orElseGet(() -> PositionPermission.builder().positionCd("TK").permissionCd("DOCUMENT_APPROVE").build());
            p.setInheritToChildren(false);positionPermissions.saveAndFlush(p);
        });
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
            returnRound(revision);
            throw new IllegalStateException("force rollback after retirement");
        }));
        assertEquals(DocumentStatus.IN_REVIEW, documents.findById(draftId).orElseThrow().getStatus());
        assertEquals(before, documents.findById(draftId).orElseThrow().getPrimaryFilePath());
        assertEquals(Boolean.FALSE, jdbc.queryForObject("select delete_flg from document_attachments where id=?", Boolean.class, oldId));
        assertTrue(attachments.findByIdAndDocumentIdAndDeleteFlgFalse(oldId, draftId).isPresent());
        assertTrue(attachments.findLatestPdfReturnAt(draftId).isEmpty());
    }

    @Test void retiringMainReloadsCurrentFlagsAndFlushesPendingChanges() {
        Long mainId = uploadRoundPdf(draftId, true, new byte[]{1});
        Long appendixId = uploadRoundPdf(draftId, false, new byte[]{2});
        tx.executeWithoutResult(s -> {
            var cachedMain = attachments.findById(mainId).orElseThrow();
            assertFalse(cachedMain.isDeleteFlg());
            assertTrue(cachedMain.isMainDocument());
            var appendix = attachments.findById(appendixId).orElseThrow();
            appendix.setOriginalFileName("pending-change.pdf");
            var document = documents.findById(draftId).orElseThrow();
            document.setSummary("Pending change before retirement");

            assertEquals(1, attachments.retireCurrentMain(draftId));

            var reloadedMain = attachments.findById(mainId).orElseThrow();
            assertTrue(reloadedMain.isDeleteFlg(), "Lookup in the same transaction must not return stale flags");
            assertFalse(reloadedMain.isMainDocument());
            assertEquals("Pending change before retirement", documents.findById(draftId).orElseThrow().getSummary());
            var reloadedAppendix = attachments.findById(appendixId).orElseThrow();
            assertEquals("pending-change.pdf", reloadedAppendix.getOriginalFileName());
            assertFalse(reloadedAppendix.isDeleteFlg());
            assertFalse(reloadedAppendix.isMainDocument());
        });
        assertEquals("Pending change before retirement", documents.findById(draftId).orElseThrow().getSummary());
        assertEquals("pending-change.pdf", attachments.findById(appendixId).orElseThrow().getOriginalFileName());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void returningWithPreloadedAttachmentPersistsDocumentAndReloadsRetiredMain(boolean revision) {
        Long mainId = uploadRoundPdf(draftId, true, new byte[]{1});
        roundSigning(); submitRound();
        tx.executeWithoutResult(s -> {
            var cachedMain = attachments.findById(mainId).orElseThrow();
            assertFalse(cachedMain.isDeleteFlg());
            returnRound(revision);
            var main = attachments.findById(mainId).orElseThrow();
            assertTrue(main.isDeleteFlg());
            assertFalse(main.isMainDocument());
            var document = documents.findById(draftId).orElseThrow();
            assertEquals(revision ? DocumentStatus.NEEDS_REVISION : DocumentStatus.DRAFT, document.getStatus());
            assertNull(document.getPrimaryFilePath());
        });
        var document = documents.findById(draftId).orElseThrow();
        assertEquals(revision ? DocumentStatus.NEEDS_REVISION : DocumentStatus.DRAFT, document.getStatus());
        assertNull(document.getPrimaryFilePath());
        assertTrue(attachments.findLatestPdfReturnAt(draftId).isPresent());
        assertTrue(attachments.findByIdAndDocumentIdAndDeleteFlgFalse(mainId, draftId).isEmpty());
    }

    @Test void signedPdfFailureDoesNotPersistRevisionOrSendRequest() {
        uploadRoundPdf(draftId, true, new byte[]{1});roundSigning();submitRound();returnRound(true);
        uploadRoundPdf(draftId, true, new byte[]{2});
        int version = documents.findById(draftId).orElseThrow().getCurrentVersion();
        int requestCount = requests.findByDocumentIdAndDeleteFlgFalseOrderBySubmittedAtDesc(draftId).size();
        when(pdfStorage.storeSignedPdf(any(), any(), eq(draftId))).thenThrow(new BusinessException(ErrorCode.DOCUMENT_PDF_STORAGE_FAILED));
        clearInvocations(notifications);
        assertThrows(BusinessException.class, this::submitRound);
        var doc=documents.findById(draftId).orElseThrow();
        assertEquals(DocumentStatus.NEEDS_REVISION, doc.getStatus());
        assertEquals(version, doc.getCurrentVersion());assertNull(doc.getPrimaryFilePath());
        assertEquals(requestCount, requests.findByDocumentIdAndDeleteFlgFalseOrderBySubmittedAtDesc(draftId).size());
        verifyNoInteractions(notifications);
    }

    @Test void uploadFailureKeepsCurrentMainAndSignedReference() {
        Long oldId=uploadRoundPdf(draftId,true,new byte[]{1});roundSigning();submitRound();
        // A normal editable draft with an existing signed reference: failed replacement must not retire it.
        tx.executeWithoutResult(s -> {var doc=documents.findById(draftId).orElseThrow();doc.setStatus(DocumentStatus.DRAFT);documents.saveAndFlush(doc);});
        String before=documents.findById(draftId).orElseThrow().getPrimaryFilePath();
        var file=new org.springframework.mock.web.MockMultipartFile("file","new.pdf","application/pdf",new byte[]{2});
        when(legacyStorage.storeFile(file,draftId)).thenThrow(new BusinessException(ErrorCode.FILE_STORAGE_ERROR));
        assertThrows(BusinessException.class,()->attachmentService.uploadAttachment(draftId,file,true,author.getId()));
        assertEquals(before,documents.findById(draftId).orElseThrow().getPrimaryFilePath());
        assertTrue(attachments.findByIdAndDocumentIdAndDeleteFlgFalse(oldId,draftId).orElseThrow().isMainDocument());
    }

    @Test void missingMainSubmissionReturnsHttp400WithoutSigning() throws Exception {
        mvc.perform(post("/documents/"+draftId+"/submit-approval").with(authentication(auth(author)))
                .contentType("application/json").content("{\"approverUserId\":"+former.getId()+"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("DOCUMENT_PRIMARY_FILE_MISSING"));
        verifyNoInteractions(signer,pdfStorage);
        assertEquals(DocumentStatus.DRAFT,documents.findById(draftId).orElseThrow().getStatus());
    }
}
