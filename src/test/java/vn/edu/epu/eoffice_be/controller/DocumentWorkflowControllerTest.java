package vn.edu.epu.eoffice_be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
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

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(documentWorkflowController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleResponse = DocumentDetailResponse.builder()
                .id(100L)
                .title("Ke hoach 2026")
                .status(DocumentStatus.IN_REVIEW)
                .officialNumber("125/QD-DHEPU")
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
                .andExpect(jsonPath("$.data.title").value("Ke hoach 2026"));
    }

    @Test
    @DisplayName("POST /documents/{id}/workflow/approve - Duyệt văn bản UC12")
    void approve_shouldReturnSuccess() throws Exception {
        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .comment("Dong y")
                .leadershipInstruction("Giao phong dao tao")
                .assignedOrgNodeId(1002L)
                .build();

        when(documentWorkflowService.approve(eq(100L), any(DocumentApproveRequest.class), anyLong()))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/documents/100/workflow/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100));
    }

    @Test
    @DisplayName("POST /documents/{id}/workflow/request-revision - Yêu cầu chỉnh sửa UC12")
    void requestRevision_shouldReturnSuccess() throws Exception {
        DocumentRevisionRequest request = DocumentRevisionRequest.builder()
                .comment("Can sua lai phan 2")
                .build();

        when(documentWorkflowService.requestRevision(eq(100L), any(DocumentRevisionRequest.class), anyLong()))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/documents/100/workflow/request-revision")
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

        when(documentWorkflowService.initialSign(eq(100L), any(DocumentInitialSignRequest.class), anyLong()))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/documents/100/workflow/initial-sign")
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

        when(documentWorkflowService.signAndPublish(eq(100L), any(DocumentPublishRequest.class), anyLong()))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/documents/100/workflow/sign-and-publish")
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

        when(documentWorkflowService.reject(eq(100L), any(DocumentRejectRequest.class), anyLong()))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/documents/100/workflow/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /documents/{id}/workflow-logs - Lịch sử luồng duyệt timeline")
    void getWorkflowLogs_shouldReturnList() throws Exception {
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

        when(documentWorkflowService.getWorkflowHistory(100L)).thenReturn(List.of(log));

        mockMvc.perform(get("/documents/100/workflow-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].action").value("SUBMIT"));
    }
}
