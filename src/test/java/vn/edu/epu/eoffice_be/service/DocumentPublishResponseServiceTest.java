package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.PublishRequest;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.UserInfo;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DocumentDistributionRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;

import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentPublishResponseServiceTest {
    @Mock DocumentRepository documents;
    @Mock DocumentDistributionRepository distributions;
    @Mock DocumentPdfStorageService storage;
    @Mock UserInfoRepository profiles;
    @InjectMocks DocumentPublishService publishing;

    private Document prepare(UserLogin drafter) {
        Document document = Document.builder().id(10L).title("Published title").drafter(drafter)
                .status(DocumentStatus.APPROVED).officialNumber("10/TEST").officialFilePath("test.pdf").build();
        when(documents.findById(10L)).thenReturn(Optional.of(document));
        when(documents.save(document)).thenReturn(document);
        when(storage.readPdf("test.pdf")).thenReturn(new byte[]{1, 2, 3});
        return document;
    }

    static Stream<Arguments> names() {
        return Stream.of(
                Arguments.of(Optional.of(UserInfo.builder().fullName("Author Full Name").build()), "Author Full Name"),
                Arguments.of(Optional.empty(), "author"),
                Arguments.of(Optional.of(UserInfo.builder().fullName(null).build()), "author"),
                Arguments.of(Optional.of(UserInfo.builder().fullName("").build()), ""));
    }

    @ParameterizedTest @MethodSource("names")
    void responsePreservesNamesFallbacksAndPublishFields(Optional<UserInfo> profile, String expectedName) {
        Document document = prepare(UserLogin.builder().id(100L).username("author").build());
        when(profiles.findByUserLoginIdAndDeleteFlgFalse(100L)).thenReturn(profile);
        PublishRequest request = new PublishRequest();
        request.setIssueDate(LocalDate.of(2026, 10, 28));
        request.setEffectiveDate(LocalDate.of(2026, 11, 1));

        var response = publishing.publishDocumentResponse(10L, request);

        assertEquals(100L, response.getDrafterId());
        assertEquals("author", response.getDrafterUsername());
        assertEquals(expectedName, response.getDrafterFullName());
        assertEquals(10L, response.getId());
        assertEquals("Published title", response.getTitle());
        assertEquals("10/TEST", response.getOfficialNumber());
        assertEquals(DocumentStatus.PUBLISHED, response.getStatus());
        assertEquals(DocumentStatus.PUBLISHED.getDescription(), response.getStatusDescription());
        assertEquals(request.getIssueDate(), response.getIssueDate());
        assertEquals(request.getEffectiveDate(), response.getEffectiveDate());
        assertEquals(document.getOfficialHash(), response.getOfficialHash());
        assertEquals("test.pdf", response.getOfficialFilePath());
        verify(profiles).findByUserLoginIdAndDeleteFlgFalse(100L);
    }

    @Test
    void missingDrafterReturnsNullAuthorFieldsWithoutProfileLookup() {
        prepare(null);
        var response = publishing.publishDocumentResponse(10L, null);
        assertNull(response.getDrafterId());
        assertNull(response.getDrafterUsername());
        assertNull(response.getDrafterFullName());
        assertEquals(DocumentStatus.PUBLISHED, response.getStatus());
        verifyNoInteractions(profiles);
    }

    @Test
    void publicationFailureReachesCallerWithoutMappingOrLookup() {
        BusinessException failure = new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        when(documents.findById(10L)).thenThrow(failure);
        assertSame(failure, assertThrows(BusinessException.class,
                () -> publishing.publishDocumentResponse(10L, null)));
        verifyNoInteractions(profiles, storage, distributions);
        verify(documents, never()).save(any());
    }
}
