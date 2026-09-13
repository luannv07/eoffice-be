package vn.edu.epu.eoffice_be.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.epu.eoffice_be.common.exception.GlobalExceptionHandler;
import vn.edu.epu.eoffice_be.security.OrgAwarePrincipal;
import vn.edu.epu.eoffice_be.service.DocumentQueryService;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DocumentQueryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DocumentQueryService documentQueryService;

    @InjectMocks
    private DocumentQueryController documentQueryController;

    private Authentication auth;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        auth = mock(Authentication.class);
        lenient().when(auth.isAuthenticated()).thenReturn(true);

        documentQueryController.registerAllowedLegacyPrincipal(ValidReflectionPrincipal.class);
        documentQueryController.registerAllowedLegacyPrincipal(PrimitiveLongOrgIdPrincipal.class);
        documentQueryController.registerAllowedLegacyPrincipal(NullOrgIdReflectionPrincipal.class);
        documentQueryController.registerAllowedLegacyPrincipal(NonLongOrgIdPrincipal.class);
        documentQueryController.registerAllowedLegacyPrincipal(ExceptionThrowingPrincipal.class);
        documentQueryController.registerAllowedLegacyPrincipal(ErrorThrowingPrincipal.class);

        var securityFilter = new org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter();
        try {
            securityFilter.afterPropertiesSet();
        } catch (Exception ignored) {}

        mockMvc = MockMvcBuilders.standaloneSetup(documentQueryController)
                .addFilters(securityFilter)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // Realistic production-like test fixture implementing UserDetails & OrgAwarePrincipal
    public static class CustomUserDetailsPrincipal implements UserDetails, OrgAwarePrincipal {
        private final Long orgId;
        private final String username;

        public CustomUserDetailsPrincipal(Long orgId, String username) {
            this.orgId = orgId;
            this.username = username;
        }

        @Override
        public Long getOrgId() {
            return orgId;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }

        @Override public String getPassword() { return "secret"; }
        @Override public String getUsername() { return username; }
        @Override public boolean isAccountNonExpired() { return true; }
        @Override public boolean isAccountNonLocked() { return true; }
        @Override public boolean isCredentialsNonExpired() { return true; }
        @Override public boolean isEnabled() { return true; }
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

    public static class PrimitiveLongOrgIdPrincipal {
        private final long orgId;
        public PrimitiveLongOrgIdPrincipal(long orgId) { this.orgId = orgId; }
        public long getOrgId() { return orgId; }
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

    public static class ErrorThrowingPrincipal {
        public Long getOrgId() { throw new AssertionError("JVM Error inside getOrgId"); }
    }

    public static class UnapprovedPrincipalWithGetOrgId {
        public Long getOrgId() { return 999L; }
    }

    @Test
    void testResolveUserOrgId_UnapprovedClassWithGetOrgId_ThrowsAccessDenied() {
        when(auth.getPrincipal()).thenReturn(new UnapprovedPrincipalWithGetOrgId());

        assertThrows(AccessDeniedException.class, () -> documentQueryController.getPublishedDocuments(auth));
        verifyNoInteractions(documentQueryService);
    }

    @Test
    void testGetPublishedDocuments_ValidOrgId_Success() {
        when(auth.getPrincipal()).thenReturn(new CustomUserDetailsPrincipal(100L, "user1"));
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
    void testGetPublishedDocuments_FallbackReflection_PrimitiveLong_Success() {
        when(auth.getPrincipal()).thenReturn(new PrimitiveLongOrgIdPrincipal(160L));
        when(documentQueryService.getPublishedDocuments(160L)).thenReturn(List.of());

        ResponseEntity<Object> response = documentQueryController.getPublishedDocuments(auth);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(documentQueryService, times(1)).getPublishedDocuments(160L);
    }

    @Test
    void testGetDocumentDetails_ValidOrgId_Success() {
        when(auth.getPrincipal()).thenReturn(new CustomUserDetailsPrincipal(200L, "user2"));
        when(documentQueryService.getDocumentDetails(1L, 200L)).thenReturn(new Object());

        ResponseEntity<Object> response = documentQueryController.getDocumentDetails(1L, auth);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(documentQueryService, times(1)).getDocumentDetails(1L, 200L);
    }

    @Test
    void testDownloadDocument_ValidOrgId_Success() {
        byte[] expectedBytes = "PDF Content".getBytes(StandardCharsets.UTF_8);
        when(auth.getPrincipal()).thenReturn(new CustomUserDetailsPrincipal(300L, "user3"));
        when(documentQueryService.downloadDocument(1L, 300L)).thenReturn(expectedBytes);

        var response = documentQueryController.downloadDocument(1L, auth);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertEquals("attachment; filename=\"document_1.pdf\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals((long) expectedBytes.length, response.getHeaders().getContentLength());
        assertNotNull(response.getBody());
        assertArrayEquals(expectedBytes, ((org.springframework.core.io.ByteArrayResource) response.getBody()).getByteArray());
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
    void testResolveUserOrgId_ReflectionRuntimeException_PropagatesOriginalException() {
        when(auth.getPrincipal()).thenReturn(new ExceptionThrowingPrincipal());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> documentQueryController.getPublishedDocuments(auth));
        assertEquals("Reflection invocation error", exception.getMessage());
        verifyNoInteractions(documentQueryService);
    }

    @Test
    void testResolveUserOrgId_ReflectionError_RethrowsErrorWithoutSwallowing() {
        when(auth.getPrincipal()).thenReturn(new ErrorThrowingPrincipal());

        AssertionError error = assertThrows(AssertionError.class, () -> documentQueryController.getPublishedDocuments(auth));
        assertEquals("JVM Error inside getOrgId", error.getMessage());
        verifyNoInteractions(documentQueryService);
    }

    @Test
    void testResolveUserOrgId_Unauthenticated_ThrowsAuthenticationException() {
        when(auth.isAuthenticated()).thenReturn(false);

        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> documentQueryController.getPublishedDocuments(auth));
        verifyNoInteractions(documentQueryService);
    }

    @Test
    void testResolveUserOrgId_AnonymousToken_ThrowsAuthenticationException() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> documentQueryController.getPublishedDocuments(anonymous));
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

    // MockMvc Integration Tests verifying Spring Security & Error Contract Enforcement
    @Test
    void testMockMvc_GetPublishedDocuments_WithValidPrincipal_Returns200() throws Exception {
        CustomUserDetailsPrincipal principal = new CustomUserDetailsPrincipal(100L, "testuser");
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(documentQueryService.getPublishedDocuments(100L)).thenReturn(List.of());

        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            mockMvc.perform(get("/documents/published")
                            .with(authentication(authentication)))
                    .andExpect(status().isOk());
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(documentQueryService, times(1)).getPublishedDocuments(100L);
    }

    @Test
    void testMockMvc_GetPublishedDocuments_Unauthenticated_Returns401() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/documents/published"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(documentQueryService);
    }

    @Test
    void testMockMvc_GetPublishedDocuments_Anonymous_Returns401() throws Exception {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        SecurityContextHolder.getContext().setAuthentication(anonymous);
        try {
            mockMvc.perform(get("/documents/published")
                            .with(authentication(anonymous)))
                    .andExpect(status().isUnauthorized());
        } finally {
            SecurityContextHolder.clearContext();
        }

        verifyNoInteractions(documentQueryService);
    }

    @Test
    void testMockMvc_GetPublishedDocuments_InvalidPrincipal_Returns403() throws Exception {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new MissingOrgIdPrincipal(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            mockMvc.perform(get("/documents/published")
                            .with(authentication(authentication)))
                    .andExpect(status().isForbidden());
        } finally {
            SecurityContextHolder.clearContext();
        }

        verifyNoInteractions(documentQueryService);
    }

    @Test
    void testMockMvc_GetPublishedDocuments_ReflectionRuntimeException_Returns500() throws Exception {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new ExceptionThrowingPrincipal(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            mockMvc.perform(get("/documents/published")
                            .with(authentication(authentication)))
                    .andExpect(status().isInternalServerError());
        } finally {
            SecurityContextHolder.clearContext();
        }

        verifyNoInteractions(documentQueryService);
    }

    @Test
    void testClassValueCache_LookupWorksConsistently() {
        ValidReflectionPrincipal principal = new ValidReflectionPrincipal(200L);
        when(auth.getPrincipal()).thenReturn(principal);
        when(auth.isAuthenticated()).thenReturn(true);
        when(documentQueryService.getPublishedDocuments(200L)).thenReturn(List.of());

        // Call 1: Computes value and caches Method in ClassValue
        ResponseEntity<Object> res1 = documentQueryController.getPublishedDocuments(auth);
        assertEquals(200, res1.getStatusCode().value());

        // Call 2: Uses cached Method from ClassValue
        ResponseEntity<Object> res2 = documentQueryController.getPublishedDocuments(auth);
        assertEquals(200, res2.getStatusCode().value());

        verify(documentQueryService, times(2)).getPublishedDocuments(200L);
    }

    @Test
    void testMockMvc_DownloadDocument_WithValidPrincipal_Returns200AndHeadersAndBody() throws Exception {
        byte[] expectedBytes = "PDF Content".getBytes(StandardCharsets.UTF_8);
        CustomUserDetailsPrincipal principal = new CustomUserDetailsPrincipal(300L, "testuser");
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(documentQueryService.downloadDocument(1L, 300L)).thenReturn(expectedBytes);

        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            mockMvc.perform(get("/documents/1/download")
                            .with(authentication(authentication)))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document_1.pdf\""))
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                    .andExpect(content().bytes(expectedBytes));
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(documentQueryService, times(1)).downloadDocument(1L, 300L);
    }

    @Test
    void testMockMvc_DownloadDocument_Unauthenticated_Returns401() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/documents/1/download"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(documentQueryService);
    }

    @Test
    void testMockMvc_DownloadDocument_MissingOrgId_Returns403() throws Exception {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new MissingOrgIdPrincipal(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            mockMvc.perform(get("/documents/1/download")
                            .with(authentication(authentication)))
                    .andExpect(status().isForbidden());
        } finally {
            SecurityContextHolder.clearContext();
        }

        verifyNoInteractions(documentQueryService);
    }
}