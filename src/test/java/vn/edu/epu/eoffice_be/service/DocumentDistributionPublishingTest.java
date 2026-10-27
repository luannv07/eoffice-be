package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.AfterEach;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.DocumentPublishRequest;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.security.pki.PdfSigner;
import vn.edu.epu.eoffice_be.service.impl.DocumentWorkflowServiceImpl;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;
import vn.edu.epu.eoffice_be.workflow.WorkflowEngine;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentDistributionPublishingTest {
    @Mock DocumentRepository documents;
    @Mock UserLoginRepository users;
    @Mock UserInfoRepository profiles;
    @Mock OrgNodeRepository orgs;
    @Mock WorkflowEngine engine;
    @Mock PdfSigner signer;
    @Mock DocumentPdfStorageService storage;
    @Mock FileStorageService files;
    @Mock DocumentSignatureGuard signatureGuard;
    @Mock DocumentWorkingPdfService workingPdf;
    @Mock DocumentDistributionRepository distributions;
    @Mock PermissionResolver permissions;
    @InjectMocks DocumentWorkflowServiceImpl service;

    private Document document;
    private UserLogin actor;

    @BeforeEach
    void setUp() {
        actor = UserLogin.builder().id(1L).username("publisher").role(Role.LEADER).build();
        document = Document.builder().id(50L).status(DocumentStatus.APPROVED)
                .issuingOrgNode(OrgNode.builder().id(30L).build())
                .documentBook(DocumentBook.builder().id(10L).build()).signer(actor).officialNumber("1/CV-EPU").primaryFilePath("storage://source").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new JwtUserPrincipal(1L, "publisher", "LEADER", true, List.of()), null, List.of()));
        when(users.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(actor));
        when(documents.findByIdAndDeleteFlgFalse(50L)).thenReturn(Optional.of(document));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    private void prepareSigning() {
        byte[] pdf = {1, 2, 3};
        PdfSigner.SignResult result = new PdfSigner.SignResult(
                "1", "publisher", "serial", "hash", new Date(), "signature", null, pdf);
        when(workingPdf.read(document)).thenReturn(pdf);
        when(signer.signAndCertifyBytes(any(), anyString(), anyString(), any(), any(), anyString(), any()))
                .thenReturn(result);
        when(storage.storeSignedPdf(any(), anyString(), eq(50L))).thenReturn("storage://official");
        when(files.computeSha256(any(byte[].class))).thenReturn("hash");
    }

    @Test
    void allStoresOneBroadcastAfterTransition() {
        prepareSigning();
        service.signAndPublish(50L, DocumentPublishRequest.builder().distributionScope(DistributionScope.ALL).build());
        var order = inOrder(engine, distributions);
        order.verify(engine).executeTransition(eq(document), eq(WorkflowAction.SIGN_AND_PUBLISH),
                any(), any(), any(), any(), eq("hash"));
        order.verify(distributions).save(argThat(d -> d.getDocumentId().equals(50L)
                && d.getTargetType().equals("ALL") && d.getTargetId() == null && !d.getIsRead()));
        verify(distributions, never()).saveAll(any());
        verifyNoInteractions(orgs, permissions);
    }

    @ParameterizedTest
    @MethodSource("validOrgSelections")
    void orgSelectionsStoreDistinctRecipients(DistributionScope scope, List<Long> ids, List<Long> expected) {
        when(orgs.findByIdInAndDeleteFlgFalse(expected)).thenReturn(expected.stream()
                .map(id -> OrgNode.builder().id(id).build()).toList());
        when(permissions.hasPermission(1L, Role.LEADER, "DOCUMENT_DISTRIBUTE", 30L)).thenReturn(true);
        prepareSigning();
        service.signAndPublish(50L, DocumentPublishRequest.builder().distributionScope(scope).recipientOrgIds(ids).build());
        verify(distributions).saveAll(argThat(rows -> {
            List<DocumentDistribution> actual = new ArrayList<>();
            rows.forEach(actual::add);
            return actual.stream().map(DocumentDistribution::getTargetId).toList().equals(expected)
                    && actual.stream().allMatch(d -> d.getDocumentId().equals(50L)
                    && d.getTargetType().equals("ORG") && !d.getIsRead());
        }));
        verify(distributions, never()).save(any());
        var order = inOrder(permissions, workingPdf);
        order.verify(permissions).hasPermission(1L, Role.LEADER, "DOCUMENT_DISTRIBUTE", 30L);
        order.verify(workingPdf).read(document);
        verifyNoMoreInteractions(permissions);
    }

    static Stream<Arguments> validOrgSelections() {
        return Stream.of(
                Arguments.of(DistributionScope.SINGLE_ORG, List.of(10L, 10L), List.of(10L)),
                Arguments.of(DistributionScope.MULTIPLE_ORGS, List.of(10L, 20L, 10L), List.of(10L, 20L)));
    }

    @ParameterizedTest
    @MethodSource("invalidSelections")
    void invalidRecipientsStopBeforeSigning(DistributionScope scope, List<Long> ids) {
        BusinessException error = assertThrows(BusinessException.class, () -> service.signAndPublish(50L,
                DocumentPublishRequest.builder().distributionScope(scope).recipientOrgIds(ids).build()));
        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        verifyNoInteractions(signer, storage, files, distributions, orgs, permissions);
        verify(engine, never()).executeTransition(any(), any(), any(), any(), any(), any(), any());
    }

    static Stream<Arguments> invalidSelections() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of(DistributionScope.ALL, List.of(10L)),
                Arguments.of(DistributionScope.SINGLE_ORG, null),
                Arguments.of(DistributionScope.SINGLE_ORG, List.of()),
                Arguments.of(DistributionScope.SINGLE_ORG, List.of(10L, 20L)),
                Arguments.of(DistributionScope.MULTIPLE_ORGS, List.of(10L, 10L)),
                Arguments.of(DistributionScope.MULTIPLE_ORGS, Arrays.asList(10L, null)),
                Arguments.of(DistributionScope.MULTIPLE_ORGS, List.of(10L, 0L)),
                Arguments.of(DistributionScope.MULTIPLE_ORGS, Collections.nCopies(201, 10L)));
    }

    @Test
    void missingOrDeletedOrgStopsBeforeSigning() {
        when(orgs.findByIdInAndDeleteFlgFalse(List.of(10L))).thenReturn(List.of());
        BusinessException error = assertThrows(BusinessException.class, () -> service.signAndPublish(50L,
                DocumentPublishRequest.builder().distributionScope(DistributionScope.SINGLE_ORG)
                        .recipientOrgIds(List.of(10L)).build()));
        assertEquals(ErrorCode.ORG_NODE_NOT_FOUND, error.getErrorCode());
        verifyNoInteractions(signer, storage, distributions, permissions);
    }

    @Test
    void transitionFailureDoesNotDistribute() {
        prepareSigning();
        doThrow(new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE)).when(engine)
                .executeTransition(any(), any(), any(), any(), any(), any(), any());
        assertThrows(BusinessException.class, () -> service.signAndPublish(50L,
                DocumentPublishRequest.builder().distributionScope(DistributionScope.ALL).build()));
        verifyNoInteractions(distributions);
    }

    @Test
    void distributionFailurePropagatesToTransactionalCaller() {
        prepareSigning();
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(distributions.save(any())).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.signAndPublish(50L,
                DocumentPublishRequest.builder().distributionScope(DistributionScope.ALL).build())));
    }

    static Stream<Arguments> scopedRoles() {
        return Stream.of(DistributionScope.SINGLE_ORG, DistributionScope.MULTIPLE_ORGS)
                .flatMap(scope -> Arrays.stream(Role.values()).map(role -> Arguments.of(scope, role)));
    }

    private DocumentPublishRequest validScopedRequest(DistributionScope scope) {
        List<Long> recipients = scope == DistributionScope.SINGLE_ORG ? List.of(10L) : List.of(10L, 20L);
        when(orgs.findByIdInAndDeleteFlgFalse(recipients)).thenReturn(recipients.stream()
                .map(id -> OrgNode.builder().id(id).build()).toList());
        return DocumentPublishRequest.builder().distributionScope(scope).recipientOrgIds(recipients).build();
    }

    private void assertPublicationStopped() {
        verifyNoInteractions(workingPdf, signatureGuard, signer, storage, files, distributions);
        verify(engine, never()).executeTransition(any(), any(), any(), any(), any(), any(), any());
        verify(documents, never()).save(any());
        assertEquals(DocumentStatus.APPROVED, document.getStatus());
    }

    @ParameterizedTest
    @MethodSource("scopedRoles")
    void missingIssuingPermissionRejectsEveryRole(DistributionScope scope, Role role) {
        actor.setRole(role);
        DocumentPublishRequest request = validScopedRequest(scope);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.signAndPublish(50L, request));

        assertEquals(ErrorCode.FORBIDDEN, failure.getErrorCode());
        assertEquals("Bạn không có quyền phân phối văn bản của đơn vị ban hành", failure.getMessage());
        verify(permissions).hasPermission(1L, role, "DOCUMENT_DISTRIBUTE", 30L);
        verifyNoMoreInteractions(permissions);
        assertPublicationStopped();
    }

    @ParameterizedTest
    @MethodSource("scopedRoles")
    void issuingPermissionUsesDatabaseRoleAndAllowsOtherRecipientOrgs(DistributionScope scope, Role role) {
        actor.setRole(role);
        Role tokenRole = role == Role.USER ? Role.ADMIN : Role.USER;
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new JwtUserPrincipal(1L, "publisher", tokenRole.name(), true, List.of()), null, List.of()));
        DocumentPublishRequest request = validScopedRequest(scope);
        when(permissions.hasPermission(1L, role, "DOCUMENT_DISTRIBUTE", 30L)).thenReturn(true);
        prepareSigning();

        service.signAndPublish(50L, request);

        verify(permissions).hasPermission(1L, role, "DOCUMENT_DISTRIBUTE", 30L);
        verifyNoMoreInteractions(permissions);
        verify(distributions).saveAll(any());
    }

    static Stream<Arguments> missingAuthorizationContext() {
        return Stream.of(DistributionScope.SINGLE_ORG, DistributionScope.MULTIPLE_ORGS)
                .flatMap(scope -> Stream.of(true, false).map(missingActorId -> Arguments.of(scope, missingActorId)));
    }

    @ParameterizedTest
    @MethodSource("missingAuthorizationContext")
    void missingActorIdOrIssuingOrgRejectsBeforeSigning(DistributionScope scope, boolean missingActorId) {
        if (missingActorId) {
            actor.setId(null);
        } else {
            document.setIssuingOrgNode(null);
            document.setIssuingOrg(null);
        }
        DocumentPublishRequest request = validScopedRequest(scope);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.signAndPublish(50L, request));

        assertEquals(ErrorCode.FORBIDDEN, failure.getErrorCode());
        verifyNoInteractions(permissions);
        assertPublicationStopped();
    }
}
