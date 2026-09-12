package vn.edu.epu.eoffice_be.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.epu.eoffice_be.common.exception.GlobalExceptionHandler;
import vn.edu.epu.eoffice_be.dto.PendingDocumentResponse;
import vn.edu.epu.eoffice_be.dto.WorkflowTimelineResponse;
import vn.edu.epu.eoffice_be.service.DocumentWorkflowService;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DocumentWorkflowControllerTest {

    MockMvc mockMvc;

    @Mock
    DocumentWorkflowService workflowService;

    @InjectMocks
    DocumentWorkflowController controller;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        var securityFilter = new org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter();
        try {
            securityFilter.afterPropertiesSet();
        } catch (Exception ignored) {}

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(securityFilter)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
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

        when(workflowService.getDocumentTimeline(eq(10L), eq(auth))).thenReturn(List.of(timelineItem));

        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            mockMvc.perform(get("/documents/10/timeline")
                            .with(authentication(auth)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(1L))
                    .andExpect(jsonPath("$[0].documentId").value(10L))
                    .andExpect(jsonPath("$[0].action").value("SUBMIT_APPROVAL"));
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(workflowService, times(1)).getDocumentTimeline(eq(10L), eq(auth));
    }

    @Test
    void testGetDocumentTimeline_Unauthenticated_Returns403() throws Exception {
        SecurityContextHolder.clearContext();
        // Enforced at API/controller authorization layer without stubbing workflowService -> 403 Forbidden
        mockMvc.perform(get("/documents/99/timeline"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(workflowService);
    }

    @Test
    void testGetDocumentTimeline_AnonymousToken_Returns403() throws Exception {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        SecurityContextHolder.getContext().setAuthentication(anonymous);
        try {
            mockMvc.perform(get("/documents/99/timeline")
                            .with(authentication(anonymous)))
                    .andExpect(status().isForbidden());
        } finally {
            SecurityContextHolder.clearContext();
        }

        verifyNoInteractions(workflowService);
    }

    @Test
    void testGetDocumentTimeline_ServiceThrowsAccessDenied_ExceptionHandlerReturns403() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", "pass", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        when(workflowService.getDocumentTimeline(eq(99L), eq(auth)))
                .thenThrow(new AccessDeniedException("User is not authorized to view timeline"));

        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            mockMvc.perform(get("/documents/99/timeline")
                            .with(authentication(auth)))
                    .andExpect(status().isForbidden());
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(workflowService, times(1)).getDocumentTimeline(eq(99L), eq(auth));
    }

    @Test
    void testMethodSecurityConfigurationAndEnforcement() {
        // [16/20] 1. Kiểm tra cấu hình method security toàn cục @EnableMethodSecurity(prePostEnabled = true)
        org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity enableMethodSecurity =
                vn.edu.epu.eoffice_be.config.SecurityConfig.class.getAnnotation(
                        org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity.class);
        assertNotNull(enableMethodSecurity, "SecurityConfig must be annotated with @EnableMethodSecurity");
        assertTrue(enableMethodSecurity.prePostEnabled(), "prePostEnabled must be true for @PreAuthorize enforcement");

        // [16/20] 2. Kiểm tra annotation @PreAuthorize trên controller
        PreAuthorize annotation = DocumentWorkflowController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, "DocumentWorkflowController must be annotated with @PreAuthorize");
        assertEquals("isAuthenticated()", annotation.value());

        // [16/20] [4/17] 3. Kiểm tra hành vi: khi SecurityContext trống, yêu cầu bị từ chối với AccessDeniedException (403)
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class,
                () -> controller.getDocumentTimeline(99L, null));
        verifyNoInteractions(workflowService);
    }
}