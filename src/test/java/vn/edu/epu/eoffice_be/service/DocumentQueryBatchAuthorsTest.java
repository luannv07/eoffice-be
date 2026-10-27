package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentBook;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository.DrafterName;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentQueryBatchAuthorsTest {
    enum Route { ORG, ORG_BOOK, ALL, ALL_BOOK, NULL_ORG, NULL_ORG_BOOK }

    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final UserInfoRepository profiles = mock(UserInfoRepository.class);
    private final DocumentQueryService service = new DocumentQueryService(documents,
            mock(DocumentDistributionRepository.class), mock(DocumentPdfStorageService.class), profiles);

    private void stubRoute(Route route, List<Document> docs) {
        switch (route) {
            case ORG -> when(documents.findPublishedDocumentsForUser(20L)).thenReturn(docs);
            case ORG_BOOK -> when(documents.findPublishedDocumentsForUser(20L, null, 30L)).thenReturn(docs);
            case ALL, NULL_ORG -> when(documents.findByStatusOrderByIssueDateDescIdDesc(DocumentStatus.PUBLISHED)).thenReturn(docs);
            case ALL_BOOK, NULL_ORG_BOOK -> when(documents.findByStatusOrderByIssueDateDescIdDesc(DocumentStatus.PUBLISHED, 30L)).thenReturn(docs);
        }
    }

    private List<DocumentResponse> invoke(Route route) {
        return switch (route) {
            case ORG -> service.getPublishedDocuments(20L);
            case ORG_BOOK -> service.getPublishedDocuments(20L, 30L);
            case ALL -> service.getAllPublishedDocuments();
            case ALL_BOOK -> service.getAllPublishedDocuments(30L);
            case NULL_ORG -> service.getPublishedDocuments(null);
            case NULL_ORG_BOOK -> service.getPublishedDocuments(null, 30L);
        };
    }

    private static DrafterName name(Long id, String username, String fullName) {
        return new DrafterName() {
            public Long getUserId() { return id; }
            public String getUsername() { return username; }
            public String getFullName() { return fullName; }
        };
    }

    private static Document doc(Long id, UserLogin drafter) {
        return Document.builder().id(id).drafter(drafter).title("Title " + id)
                .status(DocumentStatus.PUBLISHED).officialNumber("N-" + id).officialHash("hash-" + id)
                .issueDate(Instant.parse("2026-10-27T18:00:00Z"))
                .effectiveDate(Instant.parse("2026-10-28T18:00:00Z"))
                .documentBook(DocumentBook.builder().id(30L).bookCode("BOOK").build()).build();
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void batchesUniqueAuthorsAndPreservesEveryMappedFieldAndOrder(Route route) {
        UserLogin first = mock(UserLogin.class);
        UserLogin second = mock(UserLogin.class);
        when(first.getId()).thenReturn(100L);
        when(second.getId()).thenReturn(200L);
        List<Document> docs = List.of(doc(3L, first), doc(1L, second), doc(2L, first));
        stubRoute(route, docs);
        when(profiles.findDrafterNamesByUserIds(Set.of(100L, 200L)))
                .thenReturn(List.of(name(100L, "alice", "Alice Name"), name(200L, "bob", null)));

        List<DocumentResponse> result = invoke(route);
        assertEquals(List.of(3L, 1L, 2L), result.stream().map(DocumentResponse::getId).toList());
        for (int i = 0; i < docs.size(); i++) {
            Document d = docs.get(i);
            boolean alice = d.getDrafter() == first;
            DocumentResponse expected = DocumentResponse.builder().id(d.getId()).title(d.getTitle())
                    .drafterId(alice ? 100L : 200L).drafterUsername(alice ? "alice" : "bob")
                    .drafterFullName(alice ? "Alice Name" : "bob")
                    .officialNumber(d.getOfficialNumber()).officialHash(d.getOfficialHash())
                    .documentBookId(30L).documentBookCode("BOOK").status(DocumentStatus.PUBLISHED)
                    .statusDescription(DocumentStatus.PUBLISHED.getDescription())
                    .issueDate(LocalDate.of(2026, 10, 28)).effectiveDate(LocalDate.of(2026, 10, 29)).build();
            assertEquals(expected, result.get(i));
        }
        verify(first, never()).getUsername();
        verify(second, never()).getUsername();
        verify(profiles).findDrafterNamesByUserIds(Set.of(100L, 200L));
        verifyNoMoreInteractions(profiles);
        // Only the exact route stub is used, so filters cannot silently widen.
        switch (route) {
            case ORG -> verify(documents).findPublishedDocumentsForUser(20L);
            case ORG_BOOK -> verify(documents).findPublishedDocumentsForUser(20L, null, 30L);
            case ALL, NULL_ORG -> verify(documents).findByStatusOrderByIssueDateDescIdDesc(DocumentStatus.PUBLISHED);
            case ALL_BOOK, NULL_ORG_BOOK -> verify(documents).findByStatusOrderByIssueDateDescIdDesc(DocumentStatus.PUBLISHED, 30L);
        }
        verifyNoMoreInteractions(documents);
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void emptyListDoesNotLookupAuthors(Route route) {
        stubRoute(route, List.of());
        assertTrue(invoke(route).isEmpty());
        verifyNoInteractions(profiles);
    }

    @Test
    void noAuthorOrAuthorIdSkipsBatchAndPreservesEntityFallback() {
        stubRoute(Route.ALL, List.of(doc(1L, null), doc(2L, UserLogin.builder().username("legacy").build())));
        var result = invoke(Route.ALL);
        assertNull(result.getFirst().getDrafterId());
        assertNull(result.getFirst().getDrafterUsername());
        assertNull(result.getFirst().getDrafterFullName());
        assertNull(result.getLast().getDrafterId());
        assertEquals("legacy", result.getLast().getDrafterUsername());
        assertEquals("legacy", result.getLast().getDrafterFullName());
        verifyNoInteractions(profiles);
    }

    @Test
    void keepsEmptyNameAndFallsBackToUsernameForNullNameOrMissingProjection() {
        stubRoute(Route.ALL, List.of(doc(1L, UserLogin.builder().id(10L).build()),
                doc(2L, UserLogin.builder().id(20L).build()),
                doc(3L, UserLogin.builder().id(30L).username("fallback").build())));
        when(profiles.findDrafterNamesByUserIds(Set.of(10L, 20L, 30L)))
                .thenReturn(List.of(name(10L, "empty", ""), name(20L, "no-profile", null)));
        var result = invoke(Route.ALL);
        assertEquals("", result.get(0).getDrafterFullName());
        assertEquals("no-profile", result.get(1).getDrafterFullName());
        assertEquals("fallback", result.get(2).getDrafterUsername());
        assertEquals("fallback", result.get(2).getDrafterFullName());
        verify(profiles).findDrafterNamesByUserIds(Set.of(10L, 20L, 30L));
        verifyNoMoreInteractions(profiles);
    }

    @Test
    void propagatesBatchFailureWithoutSingleProfileFallback() {
        stubRoute(Route.ALL, List.of(doc(1L, UserLogin.builder().id(10L).build())));
        RuntimeException failure = new IllegalStateException("repository unavailable");
        when(profiles.findDrafterNamesByUserIds(Set.of(10L))).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> invoke(Route.ALL)));
        verify(profiles).findDrafterNamesByUserIds(Set.of(10L));
        verifyNoMoreInteractions(profiles);
    }
}
