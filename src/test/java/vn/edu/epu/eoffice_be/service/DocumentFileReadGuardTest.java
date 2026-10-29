package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DocumentFileReadGuardTest {
    StoredFileMetadataRepository metadata;
    DocumentRepository documents;
    UserLoginRepository users;
    DocumentApprovalRequestRepository requests;
    DocumentFileReadGuard guard;
    @BeforeEach void setup() {
        metadata = mock(StoredFileMetadataRepository.class); documents = mock(DocumentRepository.class);
        users = mock(UserLoginRepository.class); requests = mock(DocumentApprovalRequestRepository.class);
        guard = new DocumentFileReadGuard(metadata, documents, new DocumentReadPolicy(users, requests));
        var user = UserLogin.builder().id(10L).role(Role.USER).build();
        when(users.findByIdAndDeleteFlgFalse(10L)).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new JwtUserPrincipal(10L, "admin", "ADMIN", true, List.of()), null, List.of()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    StoredFileMetadata file(long id, String fileId, Map<String,String> extra) {
        return StoredFileMetadata.builder().id(id).fileId(fileId).provider("test").metadata(extra).build();
    }
    Document doc(long id, long owner, String reference) {
        return Document.builder().id(id).drafter(UserLogin.builder().id(owner).build())
                .status(DocumentStatus.DRAFT).primaryFilePath(reference).build();
    }
    @Test void listUsesOneBatchForMetadataDocumentsAndHistoryRegardlessOfFileCount() {
        var owned = doc(1,10,"storage://101");
        var historical = doc(2,20,"storage://102");
        var hidden = doc(3,20,"storage://103");
        var records = List.of(file(101,"owned",Map.of()), file(102,"old",Map.of()),
                file(103,"hidden",Map.of()), file(104,"metadata-link",Map.of("documentId","2")));
        when(metadata.findByProviderAndFileIdIn(eq("test"),anyCollection())).thenReturn(records);
        when(documents.findFileLinkedDocuments(Set.of(2L),Set.of("storage://101","storage://102","storage://103","storage://104")))
                .thenReturn(List.of(owned,historical,hidden));
        when(requests.findParticipantDocumentIds(List.of(1L,2L,3L),10L)).thenReturn(List.of(2L));
        assertEquals(Set.of("owned","old","metadata-link","unbound"),guard.visibleFileIds("test",List.of("owned","old","hidden","metadata-link","unbound")));
        verify(metadata,times(1)).findByProviderAndFileIdIn(eq("test"),anyCollection());
        verify(documents,times(1)).findFileLinkedDocuments(anyCollection(),anyCollection());
        verify(users,times(1)).findByIdAndDeleteFlgFalse(10L);
        verify(requests,times(1)).findParticipantDocumentIds(anyCollection(),eq(10L));
        verify(requests,never()).existsParticipant(anyLong(),anyLong());
        verify(documents,never()).findByIdAndDeleteFlgFalse(anyLong());
        verify(metadata,never()).findByProviderAndFileId(anyString(),anyString());
    }
    @Test void emptyListDoesNotQueryAnything() {
        assertTrue(guard.visibleFileIds("test",List.of()).isEmpty());
        verifyNoInteractions(metadata,documents,requests);
        verify(users,never()).findByIdAndDeleteFlgFalse(any());
    }
    @Test void unboundAndPublishedFilesRetainTheirLegacyBranch() {
        var published = doc(1,20,"storage://101"); published.setStatus(DocumentStatus.PUBLISHED);
        when(metadata.findByProviderAndFileIdIn(eq("test"),anyCollection())).thenReturn(List.of(file(101,"published",Map.of())));
        when(documents.findFileLinkedDocuments(anyCollection(),anyCollection())).thenReturn(List.of(published));
        assertEquals(Set.of("published","other"),guard.visibleFileIds("test",List.of("published","other")));
        verifyNoInteractions(requests);
        verify(users).findByIdAndDeleteFlgFalse(10L);
    }
    @Test void malformedOrMissingDeclaredDocumentIsHiddenInsteadOfTreatedAsUnbound() {
        when(metadata.findByProviderAndFileIdIn(eq("test"),anyCollection())).thenReturn(List.of(
                file(101,"malformed",Map.of("documentId","bad")), file(102,"missing",Map.of("documentId","999"))));
        when(documents.findFileLinkedDocuments(anyCollection(),anyCollection())).thenReturn(List.of());
        assertEquals(Set.of("other"),guard.visibleFileIds("test",List.of("malformed","missing","other")));
    }
    @Test void singleReadChecksDocumentBeforeProviderAndRejectsOutsider() {
        var record = file(101,"hidden",Map.of("documentId","1"));
        when(metadata.findByProviderAndFileId("test","hidden")).thenReturn(Optional.of(record));
        when(documents.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(doc(1,20,null)));
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,()->guard.assertRead("test","hidden")).getErrorCode());
        verify(requests).existsParticipant(1L,10L);
    }
    @Test void directMutationOfLinkedDraftIsBlockedEvenForOwner() {
        when(metadata.findByProviderAndFileId("test","owned")).thenReturn(Optional.of(file(101,"owned",Map.of())));
        when(documents.findReferencingFile("storage://101")).thenReturn(List.of(doc(1,10,"storage://101")));
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,()->guard.assertUnboundWrite("test","owned")).getErrorCode());
        verifyNoInteractions(requests);
    }
}
