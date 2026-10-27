package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.dto.request.DocumentPublishRequest;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.security.pki.PdfSigner;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:eoffice-distribution-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@ActiveProfiles("test")
@Transactional
class DocumentDistributionIntegrationTest {
    @Autowired DocumentWorkflowService workflow;
    @Autowired DocumentRepository documents;
    @Autowired DocumentDistributionRepository distributions;
    @Autowired OrgNodeRepository orgs;
    @Autowired UserLoginRepository users;
    @Autowired DocumentBookRepository books;
    @MockBean PdfSigner signer;
    @MockBean DocumentPdfStorageService storage;
    @MockBean PermissionResolver permissions;
    @MockBean DocumentSignatureGuard signatureGuard;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private Document publish(DistributionScope scope, List<Long> recipients) {
        UserLogin actor = users.findAll().get(0);
        OrgNode issuingOrg = orgs.findAll().get(0);
        Document document = documents.saveAndFlush(Document.builder()
                .title("Distribution integration test")
                .documentTypeCd("CONG_VAN")
                .drafter(actor).issuingOrg(issuingOrg).signer(actor)
                .documentBook(books.findAll().get(0)).status(DocumentStatus.APPROVED).primaryFilePath("storage://source")
                .build());
        document.setOfficialNumber("DIST-TEST-" + document.getId());
        documents.saveAndFlush(document);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new JwtUserPrincipal(actor.getId(), actor.getUsername(), actor.getRole().name(), true, List.of()),
                null, List.of()));

        byte[] pdf = {1, 2, 3};
        PdfSigner.SignResult signed = new PdfSigner.SignResult(actor.getId().toString(), actor.getUsername(),
                "serial", "hash", new Date(), "signature", null, pdf);
        when(storage.readPdf("storage://source")).thenReturn(pdf);
        when(signer.signAndCertifyBytes(any(), anyString(), anyString(), any(), any(), anyString(), any()))
                .thenReturn(signed);
        when(storage.storeSignedPdf(any(), anyString(), eq(document.getId()))).thenReturn("storage://official");
        if (scope != DistributionScope.ALL) {
            when(permissions.hasPermission(actor.getId(), actor.getRole(), "DOCUMENT_DISTRIBUTE", issuingOrg.getId()))
                    .thenReturn(true);
        }

        workflow.signAndPublish(document.getId(), DocumentPublishRequest.builder()
                .distributionScope(scope).recipientOrgIds(recipients).build());
        documents.flush();
        return document;
    }

    @Test
    void singleOrgPublicationAppearsOnlyForRecipient() {
        List<OrgNode> nodes = orgs.findAll();
        Long recipient = nodes.get(0).getId();
        Long other = nodes.get(1).getId();
        Document document = publish(DistributionScope.SINGLE_ORG, List.of(recipient));

        assertEquals(DocumentStatus.PUBLISHED, document.getStatus());
        assertEquals(1, distributions.findByDocumentId(document.getId()).size());
        assertTrue(documents.findPublishedDocumentsForUser(recipient).stream()
                .anyMatch(d -> d.getId().equals(document.getId())));
        assertFalse(documents.findPublishedDocumentsForUser(other).stream()
                .anyMatch(d -> d.getId().equals(document.getId())));
        assertTrue(documents.findAccessiblePublishedDocument(document.getId(), recipient).isPresent());
        assertTrue(documents.findAccessiblePublishedDocument(document.getId(), other).isEmpty());
    }

    @Test
    void broadcastPublicationAppearsForBothOrgs() {
        List<OrgNode> nodes = orgs.findAll();
        Document document = publish(DistributionScope.ALL, List.of());
        DocumentDistribution distribution = distributions.findByDocumentId(document.getId()).get(0);
        assertEquals("ALL", distribution.getTargetType());
        assertNull(distribution.getTargetId());
        for (OrgNode node : nodes.subList(0, 2)) {
            assertTrue(documents.findPublishedDocumentsForUser(node.getId()).stream()
                    .anyMatch(d -> d.getId().equals(document.getId())));
            assertTrue(documents.findAccessiblePublishedDocument(document.getId(), node.getId()).isPresent());
        }
    }

    @Test
    void multipleOrgsPublicationStoresAndExposesBothRecipients() {
        List<Long> recipients = orgs.findAll().stream().limit(2).map(OrgNode::getId).toList();
        Document document = publish(DistributionScope.MULTIPLE_ORGS, recipients);
        assertEquals(2, distributions.findByDocumentId(document.getId()).size());
        for (Long recipient : recipients) {
            assertTrue(documents.findAccessiblePublishedDocument(document.getId(), recipient).isPresent());
        }
    }
}
