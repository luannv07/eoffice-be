package vn.edu.epu.eoffice_be.controller;

import vn.edu.epu.eoffice_be.common.enums.DistributionScope;

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
import vn.edu.epu.eoffice_be.dto.request.DocumentLeaderSignRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentInitialSignRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentPublishRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRejectRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRevisionRequest;
import vn.edu.epu.eoffice_be.dto.PendingDocumentResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentDetailResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentSignatureVerifyResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentWorkflowLogResponse;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.service.DocumentWorkflowService;

@ExtendWith(MockitoExtension.class)
class DocumentWorkflowControllerTest {

    @Test
    void leaderSignIsASeparateEndpoint() throws Exception {
        when(documentWorkflowService.leaderSign(eq(100L), any(DocumentLeaderSignRequest.class)))
                .thenReturn(mockDocumentDetailResponse(DocumentStatus.PENDING_APPROVAL));
        mockMvc.perform(post("/documents/100/workflow/leader-sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signaturePage\":2,\"signatureX\":40,\"signatureY\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));
        verify(documentWorkflowService).leaderSign(eq(100L), any(DocumentLeaderSignRequest.class));
        verify(documentWorkflowService, never()).approve(anyLong(), any());
    }

    @Test
    void leaderSignRejectsInvalidPosition() throws Exception {
        mockMvc.perform(post("/documents/100/workflow/leader-sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signaturePage\":0}"))
                .andExpect(status().isBadRequest());
        verify(documentWorkflowService, never()).leaderSign(anyLong(), any());
    }

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
            HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            if (auth == null || !auth.isAuthenticated()) {
                httpServletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpServletResponse.setContentType("application/json;charset=UTF-8");
                httpServletResponse.getWriter().write("{\"success\":false,\"errorCode\":\"" + ErrorCode.UNAUTHORIZED.getCode() + "\",\"message\":\"Chưa xác thực\"}");
                return;
            }
            String uri = ((jakarta.servlet.http.HttpServletRequest) request).getRequestURI();
            boolean isUserRole = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
            if (isUserRole) {
                if (uri.contains("/workflow/") || uri.endsWith("/pending-review")) {
                    httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    httpServletResponse.setContentType("application/json;charset=UTF-8");
                    httpServletResponse.getWriter().write("{\"success\":false,\"errorCode\":\"" + ErrorCode.FORBIDDEN.getCode() + "\",\"message\":\"Không có quyền truy cập\"}");
                    return;
                }
            }
            chain.doFilter(request, response);
        };

        mockMvc = MockMvcBuilders.standaloneSetup(documentWorkflowController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .addFilters(dummyAuthFilter)
                .build();

        setAuthRole("LEADER");
    }

    private void setAuthRole(String roleName) {
        JwtUserPrincipal principal = new JwtUserPrincipal(
                1L,
                "user_" + roleName.toLowerCase(),
                roleName,
                true,
                List.of("ROLE_" + roleName)
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + roleName))
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

        DocumentDetailResponse response = mockDocumentDetailResponse(DocumentStatus.APPROVED);
        when(documentWorkflowService.approve(eq(100L), any(DocumentApproveRequest.class))).thenReturn(response);

        mockMvc.perform(post("/documents/100/workflow/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

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

        DocumentDetailResponse response = mockDocumentDetailResponse(DocumentStatus.IN_REVIEW);
        when(documentWorkflowService.initialSign(eq(100L), any(DocumentInitialSignRequest.class))).thenReturn(response);

        mockMvc.perform(post("/documents/100/workflow/initial-sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("IN_REVIEW"));

        verify(documentWorkflowService).initialSign(eq(100L), any(DocumentInitialSignRequest.class));
    }

    @Test
    @DisplayName("UC14: Ký số phát hành - Trả về 200 OK và trạng thái PUBLISHED")
    void signAndPublish_shouldReturn200() throws Exception {
        DocumentPublishRequest request = DocumentPublishRequest.builder().distributionScope(DistributionScope.ALL)
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
    @DisplayName("UC14: Ký số phát hành thất bại khi chưa có số và thiếu sổ văn bản - Trả về 400 BAD_REQUEST")
    void signAndPublish_whenBookIdNullAndServiceThrowsBadRequest_shouldReturn400() throws Exception {
        DocumentPublishRequest request = DocumentPublishRequest.builder().distributionScope(DistributionScope.ALL)
                .comment("Ký ban hành")
                .build();

        when(documentWorkflowService.signAndPublish(eq(100L), any(DocumentPublishRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.BAD_REQUEST, "Bắt buộc chọn sổ văn bản để cấp số chính thức hoặc cấp số trước khi ban hành"));

        mockMvc.perform(post("/documents/100/workflow/sign-and-publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.BAD_REQUEST.getCode()));

        verify(documentWorkflowService).signAndPublish(eq(100L), any(DocumentPublishRequest.class));
    }

    @Test
    @DisplayName("UC14: Ký số phát hành thất bại khi comment vượt quá 2000 ký tự - Trả về 400 BAD_REQUEST")
    void signAndPublish_whenCommentExceedsMaxLength_shouldReturn400() throws Exception {
        String longComment = "a".repeat(2001);
        DocumentPublishRequest request = DocumentPublishRequest.builder().distributionScope(DistributionScope.ALL)
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
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void testGetPendingReviewDocuments() throws Exception {
        setAuthRole("LEADER");
        Page<PendingDocumentResponse> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(documentWorkflowService.getPendingDocuments(any(Pageable.class))).thenReturn(emptyPage);

        mockMvc.perform(get("/documents/pending-review?page=0&size=10"))
                .andExpect(status().isOk());

        verify(documentWorkflowService, atLeastOnce()).getPendingDocuments(any(Pageable.class));
        verify(documentWorkflowService, never()).getPendingDocuments();
    }

    @Test
    void testGetDocumentTimeline() throws Exception {
        setAuthRole("LEADER");
        when(documentWorkflowService.getDocumentTimeline(eq(1L), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/documents/1/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        verify(documentWorkflowService, atLeastOnce()).getDocumentTimeline(eq(1L), any());
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
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("Chặn quyền ở controller: Trả về 403 FORBIDDEN khi người dùng vai trò USER gọi endpoint duyệt")
    void whenUnauthorizedRoleAtController_shouldReturn403() throws Exception {
        setAuthRole("USER");

        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .leadershipInstruction("Duyệt trái quyền")
                .build();

        mockMvc.perform(post("/documents/100/workflow/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.FORBIDDEN.getCode()));

        verify(documentWorkflowService, never()).approve(any(), any());
    }

    @Test
    @DisplayName("Chặn quyền từ service: Trả về 403 FORBIDDEN khi nghiệp vụ service ném UNAUTHORIZED_WORKFLOW_ACTION")
    void whenUnauthorizedWorkflowAction_shouldReturn403() throws Exception {
        setAuthRole("LEADER");

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
        setAuthRole("LEADER");

        when(documentWorkflowService.getDetail(100L))
                .thenThrow(new AccessDeniedException("Access is denied"));

        mockMvc.perform(get("/documents/100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("GET /{id}: Trả về 403 FORBIDDEN khi người dùng không có quyền truy cập văn bản (Issue 1)")
    void getDetail_whenForbidden_shouldReturn403() throws Exception {
        setAuthRole("LEADER");

        when(documentWorkflowService.getDetail(100L))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/documents/100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("GET /{id}: Trả về 404 NOT_FOUND khi văn bản không tồn tại (Issue 1)")
    void getDetail_whenNotFound_shouldReturn404() throws Exception {
        setAuthRole("LEADER");

        when(documentWorkflowService.getDetail(999L))
                .thenThrow(new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        mockMvc.perform(get("/documents/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.DOCUMENT_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("GET /{id}/workflow-logs: Trả về 403 FORBIDDEN khi người dùng không có quyền truy cập (Issue 1)")
    void getWorkflowLogs_whenForbidden_shouldReturn403() throws Exception {
        setAuthRole("LEADER");

        when(documentWorkflowService.getWorkflowHistory(eq(100L), any(Pageable.class)))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/documents/100/workflow-logs"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("GET /{id}/timeline: Trả về 403 FORBIDDEN khi người dùng không có quyền truy cập (Issue 1)")
    void getDocumentTimeline_whenForbidden_shouldReturn403() throws Exception {
        setAuthRole("LEADER");

        when(documentWorkflowService.getDocumentTimeline(eq(100L), any()))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/documents/100/timeline"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("Hành vi phân quyền: Role USER không thể truy cập pending-review (HTTP 403)")
    void whenUserRoleAccessPendingReview_shouldReturn403() throws Exception {
        setAuthRole("USER");

        mockMvc.perform(get("/documents/pending-review"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("Hành vi phân quyền: Role REVIEWER có thể truy cập pending-review (HTTP 200)")
    void whenReviewerRoleAccessPendingReview_shouldReturn200() throws Exception {
        setAuthRole("REVIEWER");
        Page<PendingDocumentResponse> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(documentWorkflowService.getPendingDocuments(any(Pageable.class)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/documents/pending-review?page=0&size=10"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Hành vi phân quyền: Role ADMIN có thể truy cập pending-review (HTTP 200)")
    void whenAdminRoleAccessPendingReview_shouldReturn200() throws Exception {
        setAuthRole("ADMIN");
        Page<PendingDocumentResponse> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(documentWorkflowService.getPendingDocuments(any(Pageable.class)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/documents/pending-review?page=0&size=10"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /{id}/signatures/verify: Trả về 403 FORBIDDEN khi người dùng không có quyền truy cập văn bản")
    void verifySignatures_whenForbidden_shouldReturn403() throws Exception {
        setAuthRole("LEADER");

        when(documentWorkflowService.verifyDocumentSignatures(100L))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/documents/100/signatures/verify"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("GET /{id}/signatures/verify: Trả về 200 OK khi người dùng có quyền truy cập văn bản")
    void verifySignatures_whenAuthorized_shouldReturn200() throws Exception {
        setAuthRole("USER");

        DocumentSignatureVerifyResponse mockResponse = DocumentSignatureVerifyResponse.builder()
                .documentId(100L)
                .documentTitle("Kế hoạch công tác năm 2026")
                .overallStatus("VALID")
                .overallStatusDesc("HỢP LỆ")
                .totalSignatures(1)
                .build();

        when(documentWorkflowService.verifyDocumentSignatures(100L))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/documents/100/signatures/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.documentId").value(100L))
                .andExpect(jsonPath("$.data.overallStatus").value("VALID"));
    }

    @Test
    @DisplayName("Xác minh cấu hình bảo mật: DocumentWorkflowController có đầy đủ @PreAuthorize và @Auditable (Issue 19)")
    void verifySecurityAnnotationsOnClassAndMethods() throws NoSuchMethodException {
        // Class annotations
        assertTrue(DocumentWorkflowController.class.isAnnotationPresent(Auditable.class));
        assertTrue(DocumentWorkflowController.class.isAnnotationPresent(PreAuthorize.class));

        // Method annotations existence
        assertNotNull(DocumentWorkflowController.class.getMethod("getDetail", Long.class).getAnnotation(PreAuthorize.class));
        assertNotNull(DocumentWorkflowController.class.getMethod("getWorkflowLogs", Long.class, Pageable.class).getAnnotation(PreAuthorize.class));
        assertNotNull(DocumentWorkflowController.class.getMethod("approve", Long.class, DocumentApproveRequest.class).getAnnotation(PreAuthorize.class));
        assertNotNull(DocumentWorkflowController.class.getMethod("requestRevision", Long.class, DocumentRevisionRequest.class).getAnnotation(PreAuthorize.class));
        assertNotNull(DocumentWorkflowController.class.getMethod("initialSign", Long.class, DocumentInitialSignRequest.class).getAnnotation(PreAuthorize.class));
        assertNotNull(DocumentWorkflowController.class.getMethod("signAndPublish", Long.class, DocumentPublishRequest.class).getAnnotation(PreAuthorize.class));
        assertNotNull(DocumentWorkflowController.class.getMethod("reject", Long.class, DocumentRejectRequest.class).getAnnotation(PreAuthorize.class));
        assertNotNull(DocumentWorkflowController.class.getMethod("getDocumentTimeline", Long.class).getAnnotation(PreAuthorize.class));
        assertNotNull(DocumentWorkflowController.class.getMethod("getPendingReviewDocuments", Pageable.class).getAnnotation(PreAuthorize.class));
        assertNotNull(DocumentWorkflowController.class.getMethod("verifySignatures", Long.class).getAnnotation(PreAuthorize.class));
        assertNotNull(DocumentWorkflowController.class.getMethod("verifySignatures", Long.class).getAnnotation(Auditable.class));
    }
}
