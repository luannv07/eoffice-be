package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.repository.DocumentDistributionRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentIntegrityVerificationTest {
    @org.junit.jupiter.api.BeforeEach
    void publishingAccountFixture() {
        var account=vn.edu.epu.eoffice_be.entity.UserLogin.builder().id(9001L).role(vn.edu.epu.eoffice_be.common.enums.Role.CLERK).build();
        org.mockito.Mockito.lenient().when(draftReadPolicy.currentUser()).thenReturn(account);
        org.mockito.Mockito.lenient().when(draftReadPolicy.authenticatedUser(org.mockito.ArgumentMatchers.any())).thenReturn(account);
    }

    @org.mockito.Mock private vn.edu.epu.eoffice_be.service.DocumentReadPolicy draftReadPolicy;

    @Mock DocumentRepository documents;
    @Mock DocumentDistributionRepository distributions;
    @Mock DocumentPdfStorageService storage;
    @Mock
    private vn.edu.epu.eoffice_be.repository.UserInfoRepository userInfoRepository;

    @InjectMocks DocumentPublishService service;

    private static final String SHA256_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private final Authentication authenticated = new UsernamePasswordAuthenticationToken("tester", null, List.of());

    @Test
    void rejectsMissingPdfStorageAtConstruction() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new DocumentPublishService(documents, distributions, null, null, userInfoRepository, org.mockito.Mockito.mock(vn.edu.epu.eoffice_be.service.DocumentReadPolicy.class)));

        assertEquals("documentPdfStorageService must not be null", exception.getMessage());
        verifyNoInteractions(documents, distributions, storage);
    }

    @Test
    void verifiesHashOfActualDownloadedContentAndDetectsTampering() {
        prepare();
        when(storage.readPdf("storage://36001")).thenReturn("abc".getBytes(StandardCharsets.UTF_8));
        assertTrue(service.verifyIntegrity(5001L, authenticated));

        when(storage.readPdf("storage://36001")).thenReturn("abd".getBytes(StandardCharsets.UTF_8));
        assertFalse(service.verifyIntegrity(5001L, authenticated));
    }

    @Test
    void doesNotAcceptHashOfStorageReferenceAsContentHash() throws Exception {
        Document document = prepare();
        document.setOfficialHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("storage://36001".getBytes(StandardCharsets.UTF_8))));
        when(storage.readPdf("storage://36001")).thenReturn("abc".getBytes(StandardCharsets.UTF_8));

        assertFalse(service.verifyIntegrity(5001L, authenticated));
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {
            "DOCUMENT_PDF_NOT_FOUND", "DOCUMENT_PDF_STORAGE_UNAVAILABLE", "DOCUMENT_PDF_STORAGE_FAILED"
    })
    void storageFailureIsNotConvertedToHashMismatch(ErrorCode code) {
        prepare();
        BusinessException failure = new BusinessException(code);
        when(storage.readPdf("storage://36001")).thenThrow(failure);

        assertSame(failure, assertThrows(BusinessException.class, () -> service.verifyIntegrity(5001L, authenticated)));
    }

    @Test
    void noRecordedHashRetainsExistingUnverifiableResult() {
        Document document = prepare();
        document.setOfficialHash(null);

        assertFalse(service.verifyIntegrity(5001L, authenticated));
        verifyNoInteractions(storage);
    }

    @Test
    void authenticationIsCheckedBeforeReadingStorage() {
        prepare();
        assertThrows(AccessDeniedException.class, () -> service.verifyIntegrity(5001L, null));
        verifyNoInteractions(storage);
    }

    private Document prepare() {
        Document document = Document.builder().id(5001L).status(DocumentStatus.PUBLISHED)
                .officialFilePath("storage://36001").officialHash(SHA256_ABC).build();
        when(documents.findById(5001L)).thenReturn(Optional.of(document));
        return document;
    }
}
