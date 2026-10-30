package vn.edu.epu.eoffice_be.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.epu.eoffice_be.common.exception.GlobalExceptionHandler;
import vn.edu.epu.eoffice_be.dto.PublishRequest;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.service.DocumentPublishService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DocumentPublishControllerTest {
    @org.junit.jupiter.api.BeforeEach
    void publishingAccountFixture() {
        var account=vn.edu.epu.eoffice_be.entity.UserLogin.builder().id(9001L).role(vn.edu.epu.eoffice_be.common.enums.Role.CLERK).build();
        org.mockito.Mockito.lenient().when(draftReadPolicy.currentUser()).thenReturn(account);
        org.mockito.Mockito.lenient().when(draftReadPolicy.authenticatedUser(org.mockito.ArgumentMatchers.any())).thenReturn(account);
    }

    @org.mockito.Mock private vn.edu.epu.eoffice_be.service.DocumentReadPolicy draftReadPolicy;


    private MockMvc mockMvc;

    @Mock
    private DocumentPublishService publishService;

    @InjectMocks
    private DocumentPublishController publishController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(publishController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void testPublishDocumentEndpoint() throws Exception {
        DocumentResponse doc = DocumentResponse.builder().id(1L).status(DocumentStatus.PUBLISHED)
                .statusDescription(DocumentStatus.PUBLISHED.getDescription()).drafterFullName("Author").build();

        when(publishService.publishDocumentResponse(eq(1L), any(PublishRequest.class))).thenReturn(doc);

        String rawJson = "{\"issueDate\":\"2026-09-12\",\"isPublicToAll\":true}";

        mockMvc.perform(post("/documents/1/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drafterFullName").value("Author"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.statusDescription").value(vn.edu.epu.eoffice_be.common.enums.DocumentStatus.PUBLISHED.getDescription()));
        ArgumentCaptor<PublishRequest> request = ArgumentCaptor.forClass(PublishRequest.class);
        verify(publishService).publishDocumentResponse(eq(1L), request.capture());
        assertEquals(java.time.LocalDate.of(2026, 9, 12), request.getValue().getIssueDate());
        assertTrue(request.getValue().getIsPublicToAll());
        verify(publishService, never()).publishDocument(any(), any());
    }

    @Test
    void controllerReturnsServiceResponseWithoutRemapping() {
        PublishRequest request = new PublishRequest();
        DocumentResponse response = DocumentResponse.builder().id(10L).drafterFullName("Author").build();
        when(publishService.publishDocumentResponse(10L, request)).thenReturn(response);
        org.junit.jupiter.api.Assertions.assertSame(response, publishController.publishDocument(10L, request).getBody());
        verify(publishService).publishDocumentResponse(10L, request);
    }

    @Test
    void testVerifyIntegrityEndpoint_Authorized_Returns200() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(publishService.verifyIntegrity(eq(1L), any())).thenReturn(true);

        mockMvc.perform(get("/documents/1/verify-integrity").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(1L))
                .andExpect(jsonPath("$.valid").value(true));

        ArgumentCaptor<Authentication> authCaptor = ArgumentCaptor.forClass(Authentication.class);
        verify(publishService, times(1)).verifyIntegrity(eq(1L), authCaptor.capture());
        assertNotNull(authCaptor.getValue());
        assertEquals("tester", authCaptor.getValue().getName());
        assertTrue(authCaptor.getValue().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void testVerifyIntegrityEndpoint_WhenFalse_Returns200() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(publishService.verifyIntegrity(eq(2L), any())).thenReturn(false);

        mockMvc.perform(get("/documents/2/verify-integrity").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(2L))
                .andExpect(jsonPath("$.valid").value(false));

        ArgumentCaptor<Authentication> authCaptor = ArgumentCaptor.forClass(Authentication.class);
        verify(publishService, times(1)).verifyIntegrity(eq(2L), authCaptor.capture());
        assertNotNull(authCaptor.getValue());
        assertEquals("tester", authCaptor.getValue().getName());
        assertTrue(authCaptor.getValue().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void testVerifyIntegrityEndpoint_Unauthorized_Returns403() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "unauthorized_user", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(publishService.verifyIntegrity(eq(99L), any()))
                .thenThrow(new AccessDeniedException("User is not authorized to verify integrity"));

        mockMvc.perform(get("/documents/99/verify-integrity").principal(auth))
                .andExpect(status().isForbidden());

        ArgumentCaptor<Authentication> authCaptor = ArgumentCaptor.forClass(Authentication.class);
        verify(publishService, times(1)).verifyIntegrity(eq(99L), authCaptor.capture());
        assertNotNull(authCaptor.getValue());
        assertEquals("unauthorized_user", authCaptor.getValue().getName());
        assertTrue(authCaptor.getValue().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void testVerifyIntegrityEndpoint_MissingAuthentication_Returns403() throws Exception {
        mockMvc.perform(get("/documents/1/verify-integrity"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(publishService);
    }
}
