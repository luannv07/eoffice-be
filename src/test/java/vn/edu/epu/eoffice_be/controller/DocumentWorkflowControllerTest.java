package vn.edu.epu.eoffice_be.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;
import vn.edu.epu.eoffice_be.common.exception.GlobalExceptionHandler;
import vn.edu.epu.eoffice_be.dto.request.DocumentApproveRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentInitialSignRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentPublishRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRejectRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRevisionRequest;
import vn.edu.epu.eoffice_be.dto.PendingDocumentResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentDetailResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentWorkflowLogResponse;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.service.DocumentWorkflowService;

@ExtendWith(MockitoExtension.class)
class DocumentWorkflowControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private DocumentWorkflowService documentWorkflowService;

    @InjectMocks
    private DocumentWorkflowController documentWorkflowController;

    private Filter dummyAuthFilter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        dummyAuthFilter = (request, response, chain) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                HttpServletResponse httpServletResponse = (HttpServletResponse) response;
                httpServletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpServletResponse.setContentType("application/json;charset=UTF-8");
                httpServletResponse.getWriter().write("{\"success\":false,\"code\":\"" + ErrorCode.UNAUTHORIZED.getCode() + "\",\"message\":\"Chưa xác thực\"}");
                return;
            }
            chain.doFilter(request, response);
        };

        mockMvc = MockMvcBuilders.standaloneSetup(documentWorkflowController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .addFilters(dummyAuthFilter)
                .build();

        JwtUserPrincipal principal = new JwtUserPrincipal(
                1L,
                "leader_user",
                "LEADER",
                true,
                List.of("ROLE_LEADER")
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_LEADER"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private DocumentDetailResponse mockDocumentDetailResponse(DocumentStatus status) {
        return DocumentDetailResponse.builder()
                .id(100L)
                .title("Kế hoạch công tác năm 2026")
                .documentTypeCd("PLAN")
                .issuingOrgId(10L)
                .issuingOrgName("Phòng Đào Tạo")
                .drafterId(2L)
                .drafterUsername("user_a")
                .urgencyLevelCd("NORMAL")
                .securityLevelCd("NORMAL")
                .status(status)
                .build();
    }

    @Test
    @DisplayName("UC12: Duyệt văn bản - Trả về 200 OK cùng dữ liệu cập nhật")
    void approve_shouldReturn200() throws Exception {
        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .leadershipInstruction("Đồng ý phê duyệt kế hoạch")
                .assignedOrgNodeId(20L)
                .build();

        DocumentDetailResponse response = mockDocumentDetailResponse(DocumentStatus.PENDING_SIGN);
        when(documentWorkflowService.approve(eq(100L), any(DocumentApproveRequest.class))).thenReturn(response);

        mockMvc.perform(post("/documents/100/workflow/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING_SIGN"));

        verify(documentWorkflowService).approve(eq(100L), any(DocumentApproveRequest.class));
    }

    @Test
    @DisplayName("UC12: Duyệt văn bản thất bại khi comment vượt quá 2000 ký tự - Trả về 400 BAD_REQUEST")
    void approve_whenCommentExceedsMaxLength_shouldReturn400() throws Exception {
        String longComment = "a".repeat(2001);
        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .comment(longComment)
                .build();

        mockMvc.perform(post("/documents/100/workflow/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_ERROR.getCode()));

        verify(documentWorkflowService, never()).approve(anyLong(), any());
    }

    @Test
    @DisplayName("UC12: Duyệt văn bản thất bại khi leadershipInstruction vượt quá 2000 ký tự - Trả về 400 BAD_REQUEST")
    void approve_whenLeadershipInstructionExceedsMaxLength_shouldReturn400() throws Exception {
        String longInstruction = "b".repeat(2001);
        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .leadershipInstruction(longInstruction)
                .build();

        mockMvc.perform(post("/documents/100/workflow/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_ERROR.getCode()));

        verify(documentWorkflowService, never()).approve(anyLong(), any());
    }

    @Test
    @DisplayName("UC12: Duyệt văn bản thất bại khi assignedOrgNodeId <= 0 - Trả về 400 BAD_REQUEST")
    void approve_whenAssignedOrgNodeIdNotPositive_shouldReturn400() throws Exception {
        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .assignedOrgNodeId(0L)
                .build();

        mockMvc.perform(post("/documents/100/workflow/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_ERROR.getCode()));

        verify(documentWorkflowService, never()).approve(anyLong(), any());
    }

    @Test
    @DisplayName("UC12: Yêu cầu chỉnh sửa - Trả về 200 OK và trạng thái NEEDS_REVISION")
    void requestRevision_shouldReturn200() throws Exception {
        DocumentRevisionRequest request = DocumentRevisionRequest.builder()
                .comment("Cần bổ sung dự toán chi tiết mục 3")
                .build();

        DocumentDetailResponse response = mockDocumentDetailResponse(DocumentStatus.NEEDS_REVISION);
        when(documentWorkflowService.requestRevision(eq(100L), any(DocumentRevisionRequest.class))).thenReturn(response);

        mockMvc.perform(post("/documents/100/workflow/request-revision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("NEEDS_REVISION"));

        verify(documentWorkflowService).requestRevision(eq(100L), any(DocumentRevisionRequest.class));
    }

    @Test
    @DisplayName("UC12: Yêu cầu chỉnh sửa thất bại khi comment để trống - Trả về 400 BAD_REQUEST")
    void requestRevision_whenCommentIsBlank_shouldReturn400() throws Exception {
        DocumentRevisionRequest request = DocumentRevisionRequest.builder()
                .comment("   ")
                .build();

        mockMvc.perform(post("/documents/100/workflow/request-revision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_ERROR.getCode()));

        verify(documentWorkflowService, never()).requestRevision(anyLong(), any());
    }

    @Test
    @DisplayName("UC12: Yêu cầu chỉnh sửa thất bại khi comment vượt quá 2000 ký tự - Trả về 400 BAD_REQUEST")
    void requestRevision_whenCommentExceedsMaxLength_shouldReturn400() throws Exception {
        String longComment = "a".repeat(2001);
        DocumentRevisionRequest request = DocumentRevisionRequest.builder()
                .comment(longComment)
                .build();

        mockMvc.perform(post("/documents/100/workflow/request-revision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_ERROR.getCode()));

        verify(documentWorkflowService, never()).requestRevision(anyLong(), any());
    }

    @Test
    @DisplayName("UC13: Ký nháy văn bản - Trả về 200 OK")
    void initialSign_shouldReturn200() throws Exception {
        DocumentInitialSignRequest request = DocumentInitialSignRequest.builder()
                .comment("Đã rà soát thể thức")
                .build();

        DocumentDetailResponse response = mockDocumentDetailResponse(DocumentStatus.INITIAL_SIGNED);
        when(documentWorkflowService.initialSign(eq(100L), any(DocumentInitialSignRequest.class))).thenReturn(response);

        mockMvc.perform(post("/documents/100/workflow/initial-sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("INITIAL_SIGNED"));

        verify(documentWorkflowService).initialSign(eq(100L), any(DocumentInitialSignRequest.class));
    }

    @Test
    @DisplayName("UC14: Ký số phát hành - Trả về 200 OK và trạng thái PUBLISHED")
    void signAndPublish_shouldReturn200() throws Exception {
        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .officialNumber("123/QĐ-ĐHEPU")
                .comment("Ký ban hành")
                .build();

        DocumentDetailResponse response = mockDocumentDetailResponse(DocumentStatus.PUBLISHED);
        response.setOfficialNumber("123/QĐ-ĐHEPU");
        when(documentWorkflowService.signAndPublish(eq(100L), any(DocumentPublishRequest.class))).thenReturn(response);

        mockMvc.perform(post("/documents/100/workflow/sign-and-publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.officialNumber").value("123/QĐ-ĐHEPU"));

        verify(documentWorkflowService).signAndPublish(eq(100L), any(DocumentPublishRequest.class));
    }

    @Test
    @DisplayName("UC14: Ký số phát hành thất bại khi comment vượt quá 2000 ký tự - Trả về 400 BAD_REQUEST")
    void signAndPublish_whenCommentExceedsMaxLength_shouldReturn400() throws Exception {
        String longComment = "a".repeat(2001);
        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .officialNumber("123/QĐ-ĐHEPU")
                .comment(longComment)
                .build();

        mockMvc.perform(post("/documents/100/workflow/sign-and-publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_ERROR.getCode()));

        verify(documentWorkflowService, never()).signAndPublish(anyLong(), any());
    }

    @Test
    @DisplayName("UC14: Từ chối văn bản - Trả về 200 OK và trạng thái REJECTED")
    void reject_shouldReturn200() throws Exception {
        DocumentRejectRequest request = DocumentRejectRequest.builder()
                .comment("Nội dung không phù hợp với định hướng")
                .build();

        DocumentDetailResponse response = mockDocumentDetailResponse(DocumentStatus.REJECTED);
        when(documentWorkflowService.reject(eq(100L), any(DocumentRejectRequest.class))).thenReturn(response);

        mockMvc.perform(post("/documents/100/workflow/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        verify(documentWorkflowService).reject(eq(100L), any(DocumentRejectRequest.class));
    }

    @Test
    @DisplayName("Xem chi tiết văn bản - Trả về 200 OK")
    void getDetail_shouldReturn200() throws Exception {
        DocumentDetailResponse response = mockDocumentDetailResponse(DocumentStatus.DRAFT);
        when(documentWorkflowService.getDetail(100L)).thenReturn(response);

        mockMvc.perform(get("/documents/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100L));

        verify(documentWorkflowService).getDetail(100L);
    }

    @Test
    @DisplayName("Lịch sử luồng duyệt - Trả về danh sách có phân trang")
    void getWorkflowLogs_shouldReturnPage() throws Exception {
        DocumentWorkflowLogResponse log = DocumentWorkflowLogResponse.builder()
                .id(1L)
                .documentId(100L)
                .action(WorkflowAction.SUBMIT)
                .actorUsername("Nguyễn Văn A")
                .fromStatus(DocumentStatus.DRAFT)
                .toStatus(DocumentStatus.PENDING_APPROVAL)
                .createdDate(Instant.now())
                .build();

        Page<DocumentWorkflowLogResponse> page = new PageImpl<>(List.of(log), PageRequest.of(0, 10), 1);
        when(documentWorkflowService.getWorkflowHistory(eq(100L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/documents/100/workflow-logs?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].action").value("SUBMIT"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("Chặn truy cập: Trả về 401 UNAUTHORIZED khi chưa xác thực người dùng")
    void whenUnauthenticated_shouldReturn401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/documents/100"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void testGetPendingReviewDocuments() throws Exception {
        Page<PendingDocumentResponse> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(documentWorkflowService.getPendingDocuments(any(Pageable.class))).thenReturn(emptyPage);

        mockMvc.perform(get("/documents/pending-review?page=0&size=10"))
                .andExpect(status().isOk());

        verify(documentWorkflowService, atLeastOnce()).getPendingDocuments(any(Pageable.class));
        verify(documentWorkflowService, never()).getPendingDocuments();
    }

    @Test
    void testGetDocumentTimeline() throws Exception {
        when(documentWorkflowService.getDocumentTimeline(anyLong())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/documents/1/timeline"))
                .andExpect(status().isOk());

        verify(documentWorkflowService, atLeastOnce()).getDocumentTimeline(1L);
    }

    @Test
    @DisplayName("Chặn truy cập: Trả về 401 UNAUTHORIZED khi người dùng chưa đăng nhập gọi endpoint duyệt")
    void whenUnauthenticatedSensitiveWorkflow_shouldReturn401() throws Exception {
        SecurityContextHolder.clearContext();

        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .leadershipInstruction("Duyệt")
                .build();

        mockMvc.perform(post("/documents/100/workflow/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("Chặn quyền: Trả về 403 FORBIDDEN khi người dùng không đủ thẩm quyền thực hiện hành động luồng duyệt")
    void whenUnauthorizedWorkflowAction_shouldReturn403() throws Exception {
        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .leadershipInstruction("Duyệt trái thẩm quyền")
                .build();

        when(documentWorkflowService.approve(eq(100L), any(DocumentApproveRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION));

        mockMvc.perform(post("/documents/100/workflow/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHORIZED_WORKFLOW_ACTION.getCode()));
    }

    @Test
    @DisplayName("Chặn quyền: Trả về 403 FORBIDDEN khi AccessDeniedException được ném")
    void whenAccessDenied_shouldReturn403() throws Exception {
        when(documentWorkflowService.getDetail(100L))
                .thenThrow(new AccessDeniedException("Access is denied"));

        mockMvc.perform(get("/documents/100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("Xác minh cấu hình bảo mật: DocumentWorkflowController có đầy đủ @PreAuthorize và @Auditable")
    void verifySecurityAnnotationsOnClassAndMethods() throws NoSuchMethodException {
        // Class annotations
        assertTrue(DocumentWorkflowController.class.isAnnotationPresent(Auditable.class));
        PreAuthorize classAuth = DocumentWorkflowController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(classAuth);
        assertEquals("isAuthenticated()", classAuth.value());

        // Method: getDetail
        PreAuthorize getDetailAuth = DocumentWorkflowController.class
                .getMethod("getDetail", Long.class)
                .getAnnotation(PreAuthorize.class);
        assertNotNull(getDetailAuth);
        assertEquals("isAuthenticated()", getDetailAuth.value());

        // Method: getWorkflowLogs
        PreAuthorize logsAuth = DocumentWorkflowController.class
                .getMethod("getWorkflowLogs", Long.class, Pageable.class)
                .getAnnotation(PreAuthorize.class);
        assertNotNull(logsAuth);
        assertEquals("isAuthenticated()", logsAuth.value());

        // Method: approve
        PreAuthorize approveAuth = DocumentWorkflowController.class
                .getMethod("approve", Long.class, DocumentApproveRequest.class)
                .getAnnotation(PreAuthorize.class);
        assertNotNull(approveAuth);
        assertEquals("hasAnyRole('LEADER', 'REVIEWER', 'ADMIN', 'SYSTEM_ADMIN')", approveAuth.value());

        // Method: requestRevision
        PreAuthorize revisionAuth = DocumentWorkflowController.class
                .getMethod("requestRevision", Long.class, DocumentRevisionRequest.class)
                .getAnnotation(PreAuthorize.class);
        assertNotNull(revisionAuth);
        assertEquals("hasAnyRole('LEADER', 'REVIEWER', 'ADMIN', 'SYSTEM_ADMIN')", revisionAuth.value());

        // Method: initialSign
        PreAuthorize signAuth = DocumentWorkflowController.class
                .getMethod("initialSign", Long.class, DocumentInitialSignRequest.class)
                .getAnnotation(PreAuthorize.class);
        assertNotNull(signAuth);
        assertEquals("hasAnyRole('REVIEWER', 'LEADER', 'ADMIN', 'SYSTEM_ADMIN')", signAuth.value());

        // Method: signAndPublish
        PreAuthorize publishAuth = DocumentWorkflowController.class
                .getMethod("signAndPublish", Long.class, DocumentPublishRequest.class)
                .getAnnotation(PreAuthorize.class);
        assertNotNull(publishAuth);
        assertEquals("hasAnyRole('LEADER', 'ADMIN', 'SYSTEM_ADMIN')", publishAuth.value());

        // Method: reject
        PreAuthorize rejectAuth = DocumentWorkflowController.class
                .getMethod("reject", Long.class, DocumentRejectRequest.class)
                .getAnnotation(PreAuthorize.class);
        assertNotNull(rejectAuth);
        assertEquals("hasAnyRole('LEADER', 'REVIEWER', 'ADMIN', 'SYSTEM_ADMIN')", rejectAuth.value());

        // Method: getDocumentTimeline
        PreAuthorize timelineAuth = DocumentWorkflowController.class
                .getMethod("getDocumentTimeline", Long.class)
                .getAnnotation(PreAuthorize.class);
        assertNotNull(timelineAuth);
        assertEquals("isAuthenticated()", timelineAuth.value());

        // Method: getPendingReviewDocuments
        PreAuthorize pendingAuth = DocumentWorkflowController.class
                .getMethod("getPendingReviewDocuments", Pageable.class)
                .getAnnotation(PreAuthorize.class);
        assertNotNull(pendingAuth);
        assertEquals("hasAnyRole('LEADER', 'REVIEWER', 'ADMIN', 'SYSTEM_ADMIN')", pendingAuth.value());
    }
}