package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.*;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.security.pki.PdfSigner;
import vn.edu.epu.eoffice_be.service.impl.DocumentWorkflowServiceImpl;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;
import vn.edu.epu.eoffice_be.workflow.WorkflowEngine;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewProcessingAuthorizationTest {
    @Mock DocumentRepository documentRepository;
    @Mock DocumentApprovalRequestRepository requests;
    @Mock DocumentWorkflowLogRepository logs;
    @Mock UserLoginRepository userLoginRepository;
    @Mock UserInfoRepository userInfoRepository;
    @Mock PositionAssignmentRepository positionAssignmentRepository;
    @Mock DocumentAttachmentRepository documentAttachmentRepository;
    @Mock DocumentPdfStorageService documentPdfStorageService;
    @Mock vn.edu.epu.eoffice_be.storage.FileStorageService sourceStorage;
    @Mock OrgNodeRepository orgNodeRepository;
    @Mock PermissionResolver permissions;
    @Mock PdfSigner pdfSigner;
    @Mock ApplicationEventPublisher events;
    @InjectMocks DocumentWorkflowServiceImpl service;
    UserLogin actor,receiver;
    Document document;
    DocumentApprovalRequest latest;
    WorkflowEngine engine;
    static final List<WorkflowAction> ACTIONS=List.of(WorkflowAction.INITIAL_SIGN,WorkflowAction.REVIEW_APPROVE,
            WorkflowAction.REQUEST_REVISION,WorkflowAction.REJECT);
    @BeforeEach void setup() {
        receiver=UserLogin.builder().id(20L).role(Role.REVIEWER).username("dean").build();
        actor=UserLogin.builder().id(21L).role(Role.REVIEWER).username("vice-dean").build();
        document=Document.builder().id(1L).status(DocumentStatus.IN_REVIEW).primaryFilePath("storage://1")
                .currentVersion(1).issuingOrgNode(OrgNode.builder().id(100L).build()).build();
        lenient().when(userLoginRepository.findByIdAndDeleteFlgFalse(anyLong())).thenAnswer(i->
                Optional.of(i.getArgument(0).equals(20L)?receiver:actor));
        lenient().when(documentRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(document));
        latest=DocumentApprovalRequest.builder().id(5L).document(document).approverUser(receiver)
                .status(ApprovalRequestStatus.PENDING).submittedAt(Instant.now()).build();
        lenient().when(requests.findFirstByDocumentIdOrderBySubmittedAtDescIdDesc(1L)).thenAnswer(i->Optional.ofNullable(latest));
        engine=new WorkflowEngine(documentRepository,logs,permissions,mock(SubmissionRoutingService.class),
                new ReviewAssignmentPolicy(new DocumentReadPolicy(userLoginRepository,requests),requests));
        ReflectionTestUtils.setField(service,"workflowEngine",engine);
        authenticate(actor);
    }
    void authenticate(UserLogin user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new JwtUserPrincipal(user.getId(),user.getUsername(),"REVIEWER",true,List.of()),null,List.of()));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    Object act(WorkflowAction action) {
        return switch(action) {
            case INITIAL_SIGN -> service.initialSign(1L,DocumentInitialSignRequest.builder().comment("review").build());
            case REVIEW_APPROVE -> service.approve(1L,DocumentApproveRequest.builder().comment("review").build());
            case REQUEST_REVISION -> service.requestRevision(1L,DocumentRevisionRequest.builder().comment("review").build());
            case REJECT -> service.reject(1L,DocumentRejectRequest.builder().comment("review").build());
            default -> throw new IllegalArgumentException();
        };
    }
    void noEffects() {
        assertEquals(DocumentStatus.IN_REVIEW,document.getStatus());assertEquals("storage://1",document.getPrimaryFilePath());
        assertEquals(1,document.getCurrentVersion());
        verifyNoInteractions(pdfSigner,documentPdfStorageService,sourceStorage,
                userInfoRepository,positionAssignmentRepository,documentAttachmentRepository,logs,events);
        verify(documentRepository,never()).save(any());verify(requests,never()).save(any());
    }
    static Stream<Arguments> roleActions() {
        return Arrays.stream(Role.values()).flatMap(role->ACTIONS.stream().map(a->Arguments.of(role,a)));
    }
    @ParameterizedTest @MethodSource("roleActions")
    void wrongReceiverCannotCauseAnyPdfOrBusinessWrites(Role role,WorkflowAction action) {
        actor.setRole(role);document.setDrafter(actor);
        lenient().when(permissions.hasPermission(anyLong(),any(Role.class),anyString(),anyLong())).thenReturn(true);
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,()->act(action)).getErrorCode());
        verifyNoInteractions(permissions);noEffects();
    }
    @ParameterizedTest @MethodSource("roleActions")
    void engineIndependentlyRejectsUnassignedAccount(Role role,WorkflowAction action) {
        actor.setRole(role);
        lenient().when(permissions.hasPermission(anyLong(),any(Role.class),anyString(),anyLong())).thenReturn(true);
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,
                ()->engine.validateActorPermission(document,actor,action)).getErrorCode());
        verifyNoInteractions(permissions);noEffects();
    }
    @ParameterizedTest @EnumSource(value=WorkflowAction.class,names={"INITIAL_SIGN","REVIEW_APPROVE","REQUEST_REVISION","REJECT"})
    void assignedReceiverUsesRealGuardAndKeepsExistingTransition(WorkflowAction action) {
        authenticate(receiver);
        when(permissions.hasPermission(eq(20L),eq(Role.REVIEWER),anyString(),eq(100L))).thenReturn(true);
        when(documentRepository.save(any())).thenAnswer(i->i.getArgument(0));
        if(action==WorkflowAction.INITIAL_SIGN || action==WorkflowAction.REVIEW_APPROVE) {
            when(documentPdfStorageService.readPdf("storage://1")).thenReturn(new byte[]{1,2});
            when(pdfSigner.signBytes(any(),eq("20"),anyString(),anyString())).thenReturn(
                    new PdfSigner.SignResult("20","dean","serial","hash",new Date(),"field",null,new byte[]{3,4}));
            when(documentPdfStorageService.storeSignedPdf(any(),anyString(),eq(1L))).thenReturn("storage://2");
        }
        assertNotNull(act(action));
        assertEquals(switch(action){case INITIAL_SIGN->DocumentStatus.IN_REVIEW;case REVIEW_APPROVE->DocumentStatus.PENDING_APPROVAL;
            case REQUEST_REVISION->DocumentStatus.NEEDS_REVISION;default->DocumentStatus.REJECTED;},document.getStatus());
        verify(logs).save(any());verify(requests,never()).save(any());
    }
    @ParameterizedTest @EnumSource(value=WorkflowAction.class,names={"INITIAL_SIGN","REVIEW_APPROVE","REQUEST_REVISION","REJECT"})
    void assignmentDoesNotGrantBusinessPermission(WorkflowAction action) {
        authenticate(receiver);
        assertEquals(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION,assertThrows(BusinessException.class,()->act(action)).getErrorCode());
        String permission=action==WorkflowAction.INITIAL_SIGN?"DOCUMENT_SIGN_DRAFT":"DOCUMENT_APPROVE";
        verify(permissions).hasPermission(20L,Role.REVIEWER,permission,100L);noEffects();
    }
    @ParameterizedTest @ValueSource(strings={"missing","deleted","approved","rejected","recalled","missingTime","missingReceiver","missingReceiverId"})
    void invalidLatestRequestNeverGrantsAuthority(String problem) {
        authenticate(receiver);
        switch(problem) {
            case "missing" -> latest=null;
            case "deleted" -> latest.setDeleteFlg(true);
            case "approved" -> latest.setStatus(ApprovalRequestStatus.APPROVED);
            case "rejected" -> latest.setStatus(ApprovalRequestStatus.REJECTED);
            case "recalled" -> latest.setStatus(ApprovalRequestStatus.RECALLED);
            case "missingTime" -> latest.setSubmittedAt(null);
            case "missingReceiver" -> latest.setApproverUser(null);
            case "missingReceiverId" -> latest.setApproverUser(new UserLogin());
        }
        assertEquals(ErrorCode.INVALID_WORKFLOW_STATE,assertThrows(BusinessException.class,
                ()->act(WorkflowAction.INITIAL_SIGN)).getErrorCode());
        verifyNoInteractions(permissions);noEffects();
    }
    @ParameterizedTest @ValueSource(strings={"disabled","deleted","roleChangedToAdmin","roleChangedToSystemAdmin"})
    void databaseAccountStateOverridesJwtReviewerClaim(String problem) {
        authenticate(receiver);
        switch(problem) {
            case "disabled" -> receiver.setUserStatusCd("DISABLED");
            case "deleted" -> receiver.setDeleteFlg(true);
            case "roleChangedToAdmin" -> receiver.setRole(Role.ADMIN);
            default -> receiver.setRole(Role.SYSTEM_ADMIN);
        }
        var error=assertThrows(BusinessException.class,()->act(WorkflowAction.INITIAL_SIGN));
        assertEquals(problem.startsWith("roleChanged")?ErrorCode.FORBIDDEN:ErrorCode.ACCOUNT_DISABLED,error.getErrorCode());
        verifyNoInteractions(permissions);noEffects();
    }
    @Test void wrongReceiverCannotProbeAssignedOrgBeforeAuthorization() {
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,()->service.approve(1L,
                DocumentApproveRequest.builder().assignedOrgNodeId(999L).comment("review").build())).getErrorCode());
        verifyNoInteractions(orgNodeRepository,permissions);noEffects();
    }

}
