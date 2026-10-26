package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.repository.DocumentDistributionRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentQueryServiceTest {
    enum Route { USER, ADMIN, STAFF, RESOURCE }

    @Mock DocumentRepository documents;
    @Mock DocumentDistributionRepository distributions;
    @Mock DocumentPdfStorageService storage;
    @InjectMocks DocumentQueryService service;

    @ParameterizedTest
    @EnumSource(Route.class)
    void authorizedDownloadsReadTheOfficialVersion(Route route) throws Exception {
        Document doc = prepare(route);
        // Distinct working and official references guard against serving the wrong version.
        doc.setPrimaryFilePath("storage://35001");
        byte[] signedPdf = {1, 2, 3, 4};
        if (route == Route.RESOURCE) {
            Resource resource = new ByteArrayResource(signedPdf);
            when(storage.readAsResource("storage://36001")).thenReturn(resource);
            assertSame(resource, download(route));
        } else {
            when(storage.readPdf("storage://36001")).thenReturn(signedPdf);
            assertArrayEquals(signedPdf, (byte[]) download(route));
        }
        verify(storage, never()).readPdf("storage://35001");
    }

    @ParameterizedTest
    @MethodSource("storageFailures")
    void storageErrorsArePreservedWithoutMockPdfFallback(Route route, ErrorCode code) {
        prepare(route);
        BusinessException failure = new BusinessException(code);
        if (route == Route.RESOURCE) when(storage.readAsResource("storage://36001")).thenThrow(failure);
        else when(storage.readPdf("storage://36001")).thenThrow(failure);

        assertSame(failure, assertThrows(BusinessException.class, () -> download(route)));
    }

    static Stream<Arguments> storageFailures() {
        return Arrays.stream(Route.values()).flatMap(route -> Stream.of(
                ErrorCode.DOCUMENT_PDF_NOT_FOUND, ErrorCode.DOCUMENT_PDF_STORAGE_UNAVAILABLE,
                ErrorCode.DOCUMENT_PDF_STORAGE_FAILED, ErrorCode.DOCUMENT_PDF_REFERENCE_INVALID)
                .map(code -> Arguments.of(route, code)));
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void inaccessibleDocumentDoesNotReadStorage(Route route) {
        // Empty repository results preserve the existing access checks on every route.
        assertThrows(IllegalArgumentException.class, () -> download(route));
        verifyNoInteractions(storage);
    }

    @ParameterizedTest
    @EnumSource(value = DocumentStatus.class, names = {"DRAFT", "ISSUED", "IN_REVIEW"})
    void staffCannotDownloadUnpublishedDocuments(DocumentStatus status) {
        Document document = prepare(Route.STAFF);
        document.setStatus(status);

        assertThrows(AccessDeniedException.class, () -> download(Route.STAFF));
        verifyNoInteractions(storage);
    }

    @ParameterizedTest
    @EnumSource(value = Route.class, names = {"USER", "ADMIN", "STAFF"})
    void legacyReferencesArePassedToTheStorageCompatibilityReader(Route route) {
        Document document = prepare(route);
        document.setOfficialFilePath("official/old.pdf");
        when(storage.readPdf("official/old.pdf")).thenReturn(new byte[]{5, 6});

        assertArrayEquals(new byte[]{5, 6}, (byte[]) download(route));
    }

    private Document prepare(Route route) {
        Document document = Document.builder().id(5001L).status(DocumentStatus.PUBLISHED)
                .issuingOrgNode(OrgNode.builder().id(10L).build()).officialFilePath("storage://36001").build();
        if (route == Route.USER || route == Route.RESOURCE) {
            when(documents.findAccessiblePublishedDocument(5001L, 10L)).thenReturn(Optional.of(document));
        } else {
            when(documents.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        }
        return document;
    }

    private Object download(Route route) {
        return switch (route) {
            case USER -> service.downloadDocument(5001L, 10L);
            case ADMIN -> service.downloadDocumentForAdmin(5001L);
            case STAFF -> service.downloadDocumentForStaff(5001L, 10L);
            case RESOURCE -> service.downloadOfficialFile(5001L, 10L, "tester", "127.0.0.1");
        };
    }
}
