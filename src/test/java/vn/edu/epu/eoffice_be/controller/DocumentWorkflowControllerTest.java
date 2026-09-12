package vn.edu.epu.eoffice_be.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.epu.eoffice_be.config.SecurityConfig;
import vn.edu.epu.eoffice_be.dto.PendingDocumentResponse;
import vn.edu.epu.eoffice_be.dto.WorkflowTimelineResponse;
import vn.edu.epu.eoffice_be.service.DocumentWorkflowService;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentWorkflowControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    DocumentWorkflowService workflowService;

    @Autowired
    DocumentWorkflowController controller;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @WithMockUser
    void testGetPendingReviewDocuments_DefaultPageable_Success() throws Exception {
        PendingDocumentResponse doc1 = PendingDocumentResponse.builder()
                .id(1L)
                .title("Doc 1")
                .status("SUBMITTED")
                .currentHolder("Văn thư tiếp nhận")
                .build();
        Page<PendingDocumentResponse> page = new PageImpl<>(List.of(doc1), PageRequest.of(0, 10), 1);

        when(workflowService.getPendingDocuments(eq(PageRequest.of(0, 10)))).thenReturn(page);

        mockMvc.perform(get("/documents/pending-review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.number").value(0));

        verify(workflowService, times(1)).getPendingDocuments(eq(PageRequest.of(0, 10)));
    }

    @Test
    @WithMockUser
    void testGetPendingReviewDocuments_EmptyPage_Success() throws Exception {
        Page<PendingDocumentResponse> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(5, 10), 0);

        when(workflowService.getPendingDocuments(eq(PageRequest.of(5, 10)))).thenReturn(emptyPage);

        mockMvc.perform(get("/documents/pending-review?page=5&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.number").value(5));

        verify(workflowService, times(1)).getPendingDocuments(eq(PageRequest.of(5, 10)));
    }

    @Test
    @WithMockUser
    void testGetPendingReviewDocuments_CustomPageAndSize_Success() throws Exception {
        List<PendingDocumentResponse> docs = List.of(
                PendingDocumentResponse.builder().id(2L).title("Doc 2").build(),
                PendingDocumentResponse.builder().id(3L).title("Doc 3").build()
        );
        Page<PendingDocumentResponse> page = new PageImpl<>(docs, PageRequest.of(1, 2), 5);

        when(workflowService.getPendingDocuments(eq(PageRequest.of(1, 2)))).thenReturn(page);

        mockMvc.perform(get("/documents/pending-review?page=1&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.number").value(1));

        verify(workflowService, times(1)).getPendingDocuments(eq(PageRequest.of(1, 2)));
    }

    @Test
    void testGetDocumentTimeline_Authorized_Returns200() throws Exception {
        WorkflowTimelineResponse timelineItem = WorkflowTimelineResponse.builder()
                .id(1L)
                .documentId(10L)
                .action("SUBMIT_APPROVAL")
                .comment("Kính gửi lãnh đạo phê duyệt")
                .build();

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", "pass", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        when(workflowService.getDocumentTimeline(eq(10L), any(Authentication.class))).thenReturn(List.of(timelineItem));

        mockMvc.perform(get("/documents/10/timeline")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].documentId").value(10L))
                .andExpect(jsonPath("$[0].action").value("SUBMIT_APPROVAL"));

        verify(workflowService, times(1)).getDocumentTimeline(eq(10L), any(Authentication.class));
    }

    @Test
    void testGetDocumentTimeline_Unauthenticated_Returns401Or403() throws Exception {
        SecurityContextHolder.clearContext();
        // Enforced at API/controller authorization layer without stubbing workflowService -> 401/403
        mockMvc.perform(get("/documents/99/timeline"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403,
                            "Expected 401 or 403 for unauthenticated request, got: " + status);
                });

        verifyNoInteractions(workflowService);
    }

    @Test
    @WithAnonymousUser
    void testGetDocumentTimeline_AnonymousToken_Returns401Or403() throws Exception {
        mockMvc.perform(get("/documents/99/timeline").with(anonymous()))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403,
                            "Expected 401 or 403 for anonymous request, got: " + status);
                });

        verifyNoInteractions(workflowService);
    }

    @Test
    void testGetDocumentTimeline_ServiceThrowsAccessDenied_ExceptionHandlerReturns403() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", "pass", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        when(workflowService.getDocumentTimeline(eq(99L), any(Authentication.class)))
                .thenThrow(new AccessDeniedException("User is not authorized to view timeline"));

        mockMvc.perform(get("/documents/99/timeline")
                        .with(authentication(auth)))
                .andExpect(status().isForbidden());

        verify(workflowService, times(1)).getDocumentTimeline(eq(99L), any(Authentication.class));
    }

    @Test
    void testMethodSecurityConfigurationAndEnforcement() throws Exception {
        // [16/20] 1. Kiểm tra cấu hình method security toàn cục @EnableMethodSecurity(prePostEnabled = true)
        EnableMethodSecurity enableMethodSecurity = SecurityConfig.class.getAnnotation(EnableMethodSecurity.class);
        assertNotNull(enableMethodSecurity, "SecurityConfig must be annotated with @EnableMethodSecurity");
        assertTrue(enableMethodSecurity.prePostEnabled(), "prePostEnabled must be true for @PreAuthorize enforcement");

        // [16/20] 2. Kiểm tra annotation @PreAuthorize trên controller
        PreAuthorize annotation = DocumentWorkflowController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, "DocumentWorkflowController must be annotated with @PreAuthorize");
        assertEquals("isAuthenticated()", annotation.value());

        // [13/17] 3. Kiểm tra controller được tạo proxy method security bởi Spring Security
        assertTrue(AopUtils.isAopProxy(controller),
                "DocumentWorkflowController must be proxied by Spring Security for @PreAuthorize enforcement");

        // [14/17] 4. Kiểm tra hành vi: khi SecurityContext trống, yêu cầu HTTP bị chặn (401/403) và không gọi workflowService
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/documents/99/timeline"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403,
                            "Expected 401 or 403 for unauthenticated request, got: " + status);
                });
        verifyNoInteractions(workflowService);
    }
}