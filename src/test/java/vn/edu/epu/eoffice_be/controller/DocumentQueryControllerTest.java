package vn.edu.epu.eoffice_be.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import vn.edu.epu.eoffice_be.security.OrgAwarePrincipal;
import vn.edu.epu.eoffice_be.service.DocumentQueryService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentQueryControllerTest {

    @Mock
    private DocumentQueryService documentQueryService;

    @InjectMocks
    private DocumentQueryController documentQueryController;

    private Authentication auth;

    @BeforeEach
    void setUp() {
        auth = mock(Authentication.class);
    }

    // Custom Principal classes for test scenarios
    public static class ValidPrincipal implements OrgAwarePrincipal {
        private final Long orgId;
        public ValidPrincipal(Long orgId) { this.orgId = orgId; }
        @Override
        public Long getOrgId() { return orgId; }
    }

    public static class ValidReflectionPrincipal {
        private final Long orgId;
        public ValidReflectionPrincipal(Long orgId) { this.orgId = orgId; }
        public Long getOrgId() { return orgId; }
    }

    public static class NullOrgIdReflectionPrincipal {
        public Long getOrgId() { return null; }
    }

    public static class MissingOrgIdPrincipal {
        public String getUsername() { return "testUser"; }
    }

    public static class NonLongOrgIdPrincipal {
        public String getOrgId() { return "ORG_STRING_NOT_LONG"; }
    }

    public static class ExceptionThrowingPrincipal {
        public Long getOrgId() { throw new RuntimeException("Reflection invocation error"); }
    }

    @Test
    void testGetPublishedDocuments_ValidOrgId_Success() {
        when(auth.getPrincipal()).thenReturn(new ValidPrincipal(100L));
        when(documentQueryService.getPublishedDocuments(100L)).thenReturn(List.of());

        ResponseEntity<Object> response = documentQueryController.getPublishedDocuments(auth);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(documentQueryService, times(1)).getPublishedDocuments(100L);
    }

    @Test
    void testGetPublishedDocuments_FallbackReflection_Success() {
        when(auth.getPrincipal()).thenReturn(new ValidReflectionPrincipal(150L));
        when(documentQueryService.getPublishedDocuments(150L)).thenReturn(List.of());

        ResponseEntity<Object> response = documentQueryController.getPublishedDocuments(auth);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(documentQueryService, times(1)).getPublishedDocuments(150L);
    }

    @Test
    void testGetDocumentDetails_ValidOrgId_Success() {
        when(auth.getPrincipal()).thenReturn(new ValidPrincipal(200L));
        when(documentQueryService.getDocumentDetails(1L, 200L)).thenReturn(new Object());

        ResponseEntity<Object> response = documentQueryController.getDocumentDetails(1L, auth);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(documentQueryService, times(1)).getDocumentDetails(1L, 200L);
    }

    @Test
    void testDownloadDocument_ValidOrgId_Success() {
        when(auth.getPrincipal()).thenReturn(new ValidPrincipal(300L));
        when(documentQueryService.downloadDocument(1L, 300L)).thenReturn("PDF Content".getBytes());

        var response = documentQueryController.downloadDocument(1L, auth);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(documentQueryService, times(1)).downloadDocument(1L, 300L);
    }

    @Test
    void testResolveUserOrgId_MissingGetOrgId_ThrowsAccessDenied() {
        when(auth.getPrincipal()).thenReturn(new MissingOrgIdPrincipal());

        assertThrows(AccessDeniedException.class, () -> documentQueryController.getPublishedDocuments(auth));
        verifyNoInteractions(documentQueryService);
    }

    @Test
    void testResolveUserOrgId_NullOrgId_ThrowsAccessDenied() {
        when(auth.getPrincipal()).thenReturn(new ValidPrincipal(null));

        assertThrows(AccessDeniedException.class, () -> documentQueryController.getPublishedDocuments(auth));
        verifyNoInteractions(documentQueryService);
    }

    @Test
    void testResolveUserOrgId_NullOrgId_FallbackReflection_ThrowsAccessDenied() {
        when(auth.getPrincipal()).thenReturn(new NullOrgIdReflectionPrincipal());

        assertThrows(AccessDeniedException.class, () -> documentQueryController.getPublishedDocuments(auth));
        verifyNoInteractions(documentQueryService);
    }

    @Test
    void testResolveUserOrgId_NonLongOrgId_ThrowsAccessDenied() {
        when(auth.getPrincipal()).thenReturn(new NonLongOrgIdPrincipal());

        assertThrows(AccessDeniedException.class, () -> documentQueryController.getPublishedDocuments(auth));
        verifyNoInteractions(documentQueryService);
    }

    @Test
    void testResolveUserOrgId_ReflectionException_PropagatesRuntimeException() {
        when(auth.getPrincipal()).thenReturn(new ExceptionThrowingPrincipal());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> documentQueryController.getPublishedDocuments(auth));
        assertEquals("Reflection invocation error", exception.getMessage());
        verifyNoInteractions(documentQueryService);
    }

    @Test
    void testResolveUserOrgId_NullAuthentication_ThrowsAuthenticationException() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> documentQueryController.getPublishedDocuments(null));
        verifyNoInteractions(documentQueryService);
    }

    @Test
    void testResolveUserOrgId_NullPrincipal_ThrowsAuthenticationException() {
        when(auth.getPrincipal()).thenReturn(null);

        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> documentQueryController.getPublishedDocuments(auth));
        verifyNoInteractions(documentQueryService);
    }
}