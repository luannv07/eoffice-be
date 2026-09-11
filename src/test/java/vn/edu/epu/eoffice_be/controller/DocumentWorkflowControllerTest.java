package vn.edu.epu.eoffice_be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;
import vn.edu.epu.eoffice_be.common.exception.GlobalExceptionHandler;
import vn.edu.epu.eoffice_be.dto.request.DocumentApproveRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentInitialSignRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentPublishRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRejectRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRevisionRequest;
import vn.edu.epu.eoffice_be.dto.response.DocumentDetailResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentWorkflowLogResponse;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.service.DocumentWorkflowService;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DocumentWorkflowControllerTest {

    @Mock
    private DocumentWorkflowService documentWorkflowService;

    @InjectMocks
    private DocumentWorkflowController documentWorkflowController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private DocumentDetailResponse sampleResponse;
    private Authentication testAuthentication;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(documentWorkflowController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        JwtUserPrincipal principal = new JwtUserPrincipal(1001L, "testuser", "SPECIALIST", true, List.of());
        testAuthentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());

        sampleResponse = DocumentDetailResponse.builder()
                .id(100L)
                .title("Ke hoach 2026")
                .status(DocumentStatus.IN_REVIEW)
                .officialNumber("125/QD-DHEPU")
                .primaryFileUrl("/documents/100/files/primary")
                .officialFileUrl("/documents/100/files/official")
                .build();
    }

    @Test
    @DisplayName("GET /documents/{id} - Lấy chi tiết văn bản")
    void getDetail_shouldReturnSuccess() throws Exception {
        when(documentWorkflowService.getDetail(100L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/documents/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.title").value("Ke hoach 2026"))
                .andExpect(jsonPath("$.data.primaryFileUrl").value("/documents/100/files/primary"))
                .andExpect(jsonPath("$.data.officialFileUrl").value("/documents/100/files/official"))
                .andExpect(jsonPath("$.data.primaryFilePath").doesNotExist())
                .andExpect(jsonPath("$.data.officialFilePath").doesNotExist());
    }

    @Test
    @DisplayName("POST /documents/{id}/workflow/approve - Duyệt văn bản UC12")
    void approve_shouldReturnSuccess() throws Exception {
        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .comment("Dong y")
                .leadershipInstruction("Giao phong dao tao")
                .assignedOrgNodeId(1002L)
                .build();

        when(documentWorkflowService.approve(eq(100L), any(DocumentApproveRequest.class), eq(1001L)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/documents/100/workflow/approve")
                        .principal(testAuthentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100));
    }

    @Test
    @DisplayName("POST /documents/{id}/workflow/approve - Ném lỗi 403 khi thiếu Authentication")
    void approve_withoutAuthentication_shouldReturnForbidden() throws Exception {
        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .comment("Dong y")
                .leadershipInstruction("Giao phong dao tao")
                .assignedOrgNodeId(1002L)
                .build();

        mockMvc.perform(post("/documents/100/workflow/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /documents/{id}/workflow/approve - Ném lỗi 403 khi Principal không phải JwtUserPrincipal")
    void approve_withInvalidPrincipal_shouldReturnForbidden() throws Exception {
        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .comment("Dong y")
                .leadershipInstruction("Giao phong dao tao")
                .assignedOrgNodeId(1002L)
                .build();

        Authentication invalidAuth = new TestingAuthenticationToken("anonymousUser", "n/a");

        mockMvc.perform(post("/documents/100/workflow/approve")
                        .principal(invalidAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /documents/{id}/workflow/request-revision - Yêu cầu chỉnh sửa UC12")
    void requestRevision_shouldReturnSuccess() throws Exception {
        DocumentRevisionRequest request = DocumentRevisionRequest.builder()
                .comment("Can sua lai phan 2")
                .build();

        when(documentWorkflowService.requestRevision(eq(100L), any(DocumentRevisionRequest.class), eq(1001L)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/documents/100/workflow/request-revision")
                        .principal(testAuthentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /documents/{id}/workflow/initial-sign - Ký nháy UC13")
    void initialSign_shouldReturnSuccess() throws Exception {
        DocumentInitialSignRequest request = DocumentInitialSignRequest.builder()
                .comment("Da tham dinh the thuc")
                .build();

        when(documentWorkflowService.initialSign(eq(100L), any(DocumentInitialSignRequest.class), eq(1001L)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/documents/100/workflow/initial-sign")
                        .principal(testAuthentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /documents/{id}/workflow/sign-and-publish - Ký phát hành PKI UC14")
    void signAndPublish_shouldReturnSuccess() throws Exception {
        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .officialNumber("125/QD-DHEPU")
                .comment("Ky ban hanh")
                .build();

        when(documentWorkflowService.signAndPublish(eq(100L), any(DocumentPublishRequest.class), eq(1001L)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/documents/100/workflow/sign-and-publish")
                        .principal(testAuthentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /documents/{id}/workflow/reject - Từ chối phê duyệt UC14")
    void reject_shouldReturnSuccess() throws Exception {
        DocumentRejectRequest request = DocumentRejectRequest.builder()
                .comment("Khong phu hop voi chu truong")
                .build();

        when(documentWorkflowService.reject(eq(100L), any(DocumentRejectRequest.class), eq(1001L)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/documents/100/workflow/reject")
                        .principal(testAuthentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /documents/{id}/workflow-logs - Lịch sử luồng duyệt timeline có phân trang")
    void getWorkflowLogs_shouldReturnPage() throws Exception {
        DocumentWorkflowLogResponse log = DocumentWorkflowLogResponse.builder()
                .id(1L)
                .documentId(100L)
                .action(WorkflowAction.SUBMIT)
                .actionName("Gửi văn bản để duyệt")
                .actorUsername("drafter")
                .fromStatus(DocumentStatus.DRAFT)
                .toStatus(DocumentStatus.SUBMITTED)
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
}
