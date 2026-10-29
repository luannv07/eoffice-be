package vn.edu.epu.eoffice_be.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.edu.epu.eoffice_be.controller.DocumentPublishController;
import vn.edu.epu.eoffice_be.dto.response.DocumentDetailResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.UserInfo;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentDrafterResponseTest {
    private final UserInfoRepository profiles = mock(UserInfoRepository.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final Document document = Document.builder().id(10L)
            .drafter(UserLogin.builder().id(100L).username("author").build()).build();

    private void profile() {
        when(profiles.findByUserLoginIdAndDeleteFlgFalse(100L))
                .thenReturn(Optional.of(UserInfo.builder().fullName("Author Full Name").build()));
    }

    private void assertContract(Object response) {
        JsonNode json = new ObjectMapper().findAndRegisterModules().valueToTree(response);
        assertEquals(100L, json.get("drafterId").longValue());
        assertEquals("author", json.get("drafterUsername").textValue());
        assertEquals("Author Full Name", json.get("drafterFullName").textValue());
        json.fieldNames().forEachRemaining(name -> assertFalse(name.startsWith("creator"), name));
    }

    @Test
    void draftAndDetailUseSameCanonicalAuthorWithoutAliases() {
        profile();
        DocumentAttachmentRepository attachments = mock(DocumentAttachmentRepository.class);
        DocumentVersionRepository versions = mock(DocumentVersionRepository.class);
        DocumentDraftService drafts = new DocumentDraftService(documents, org.mockito.Mockito.mock(vn.edu.epu.eoffice_be.service.DocumentReadPolicy.class), org.mockito.Mockito.mock(vn.edu.epu.eoffice_be.service.DraftUnitResolver.class), versions, attachments, mock(DocumentProcessLogRepository.class), mock(OrgNodeRepository.class), mock(UserLoginRepository.class), profiles, mock(DictionaryItemRepository.class));
        when(documents.findByIdAndDeleteFlgFalse(10L)).thenReturn(Optional.of(document));
        when(attachments.findByDocumentIdAndDeleteFlgFalse(10L)).thenReturn(List.of());
        when(versions.findByDocumentIdAndDeleteFlgFalseOrderByVersionNumberDesc(10L)).thenReturn(List.of());

        DocumentResponse summary = drafts.mapToDocumentResponse(document);
        DocumentDetailResponse detail = drafts.getDraftDetail(10L, 100L);
        assertContract(summary);
        assertContract(detail);
        assertContract(detail.getDocument());
    }

    @Test
    void publishedAndLegacyPublishResponsesUseSameNames() {
        profile();
        DocumentDistributionRepository distributions = mock(DocumentDistributionRepository.class);
        DocumentQueryService query = new DocumentQueryService(documents, distributions,
                mock(DocumentPdfStorageService.class), profiles);
        when(documents.findAccessiblePublishedDocument(10L, 20L)).thenReturn(Optional.of(document));
        assertContract(query.getDocumentDetail(10L, 20L));
        assertContract(query.getDocumentDetails(10L, 20L));

        DocumentPdfStorageService storage = mock(DocumentPdfStorageService.class);
        DocumentPublishService publishing = new DocumentPublishService(documents, distributions, null, storage, profiles);
        when(documents.findById(10L)).thenReturn(Optional.of(document));
        when(documents.save(document)).thenReturn(document);
        document.setOfficialFilePath("test.pdf");
        when(storage.readPdf("test.pdf")).thenReturn(new byte[]{1, 2, 3});
        assertContract(new DocumentPublishController(publishing).publishDocument(10L, null).getBody());
    }
}
