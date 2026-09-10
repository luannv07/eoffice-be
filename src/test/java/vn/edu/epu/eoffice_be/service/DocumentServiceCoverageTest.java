package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentDistribution;
import vn.edu.epu.eoffice_be.repository.DocumentDistributionRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceCoverageTest {

    @Mock
    DocumentRepository documentRepository;

    @Mock
    DocumentDistributionRepository distributionRepository;

    @InjectMocks
    DocumentQueryService documentQueryService;

    @InjectMocks
    DocumentPublishService documentPublishService;

    @Test
    void testGetDocumentDetailsAndDownload() {
        Document doc = new Document();
        doc.setId(1L);
        doc.setTitle("Test Doc");
        doc.setStatus("PUBLISHED");

        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));

        assertNotNull(documentQueryService.getDocumentDetails(1L, 10L));
        byte[] bytes = documentQueryService.downloadDocument(1L, 10L);
        assertTrue(bytes.length > 0);
    }

    @Test
    void testGetPublishedDocumentsAndDetail() {
        Document doc = new Document();
        doc.setId(1L);
        doc.setTitle("Test Doc");
        doc.setOfficialNumber("01/QD");
        doc.setStatus("PUBLISHED");
        doc.setIssueDate(LocalDate.now());

        DocumentDistribution dist = new DocumentDistribution();
        dist.setIsRead(false);

        when(documentRepository.findPublishedDocumentsForUser(10L)).thenReturn(List.of(doc));
        when(documentRepository.findAccessiblePublishedDocument(1L, 10L)).thenReturn(Optional.of(doc));
        when(distributionRepository.findByDocumentIdAndTargetId(1L, 10L)).thenReturn(Optional.of(dist));

        assertEquals(1, documentQueryService.getPublishedDocuments(10L).size());
        assertNotNull(documentQueryService.getDocumentDetail(1L, 10L));
        verify(distributionRepository, times(1)).save(any());
    }

    @Test
    void testVerifyIntegrity() {
        Document doc = new Document();
        doc.setId(1L);
        doc.setOfficialHash("abc123hash");

        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        assertTrue(documentPublishService.verifyIntegrity(1L));

        doc.setOfficialHash(null);
        assertFalse(documentPublishService.verifyIntegrity(1L));
    }
}