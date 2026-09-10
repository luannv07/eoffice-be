package vn.edu.epu.eoffice_be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.exception.GlobalExceptionHandler;
import vn.edu.epu.eoffice_be.dto.request.DocumentCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentUpdateRequest;
import vn.edu.epu.eoffice_be.dto.request.SubmitApprovalRequest;
import vn.edu.epu.eoffice_be.dto.response.DocumentAttachmentResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentDetailResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentVersionResponse;
import vn.edu.epu.eoffice_be.dto.response.EligibleApproverResponse;
import vn.edu.epu.eoffice_be.dto.response.VersionCompareResponse;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.service.DocumentApprovalService;
import vn.edu.epu.eoffice_be.service.DocumentAttachmentService;
import vn.edu.epu.eoffice_be.service.DocumentDraftService;
import vn.edu.epu.eoffice_be.service.DocumentVersionService;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    @Mock
    private DocumentDraftService documentDraftService;
    @Mock
    private DocumentAttachmentService documentAttachmentService;
    @Mock
    private DocumentVersionService documentVersionService;
    @Mock
    private DocumentApprovalService documentApprovalService;

    @InjectMocks
    private DocumentController documentController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private Authentication auth;

    @BeforeEach
    void setUp() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(documentController)
                .setMessageConverters(converter)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        JwtUserPrincipal principal = new JwtUserPrincipal(100L, "author", "USER", List.of());
        auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    @DisplayName("API POST /documents/drafts - Tạo dự thảo thành công (UC08)")
    void createDraft_Success() throws Exception {
        DocumentCreateRequest request = DocumentCreateRequest.builder()
                .title("Quyết định ban hành kế hoạch NCKH")
                .summary("Kế hoạch triển khai NCKH năm 2026")
                .content("Nội dung quyết định...")
                .documentTypeCd("QUYET_DINH")
                .issuingOrgNodeId(10L)
                .build();

        DocumentResponse response = DocumentResponse.builder()
                .id(1L)
                .draftCode("DT-2026-00001")
                .title("Quyết định ban hành kế hoạch NCKH")
                .status(DocumentStatus.DRAFT)
                .currentVersion(1)
                .build();

        when(documentDraftService.createDraft(any(DocumentCreateRequest.class), eq(100L))).thenReturn(response);

        mockMvc.perform(post("/documents/drafts")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.draftCode").value("DT-2026-00001"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("API GET /documents/{id} - Lấy chi tiết dự thảo thành công")
    void getDraftDetail_Success() throws Exception {
        DocumentResponse doc = DocumentResponse.builder()
                .id(1L)
                .draftCode("DT-2026-00001")
                .title("Dự thảo 1")
                .status(DocumentStatus.DRAFT)
                .build();

        DocumentDetailResponse detail = DocumentDetailResponse.builder()
                .document(doc)
                .attachments(Collections.emptyList())
                .versions(Collections.emptyList())
                .build();

        when(documentDraftService.getDraftDetail(eq(1L), eq(100L))).thenReturn(detail);

        mockMvc.perform(get("/documents/1").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.document.id").value(1));
    }

    @Test
    @DisplayName("API GET /documents/drafts - Tìm kiếm dự thảo thành công")
    void searchDrafts_Success() throws Exception {
        DocumentResponse doc = DocumentResponse.builder().id(1L).draftCode("DT-2026-00001").build();
        when(documentDraftService.searchDrafts(any(), eq(100L)))
                .thenReturn(new PageImpl<>(List.of(doc), org.springframework.data.domain.PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/documents/drafts?keyword=kehoach").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(1));
    }

    @Test
    @DisplayName("API PUT /documents/{id} - Chỉnh sửa dự thảo thành công (UC10)")
    void updateDraft_Success() throws Exception {
        DocumentUpdateRequest request = DocumentUpdateRequest.builder()
                .title("Tiêu đề cập nhật")
                .summary("Trích yếu mới")
                .content("Nội dung mới")
                .documentTypeCd("QUYET_DINH")
                .issuingOrgNodeId(10L)
                .changeSummary("Sửa theo góp ý")
                .saveNewVersion(true)
                .build();

        DocumentResponse response = DocumentResponse.builder()
                .id(1L)
                .title("Tiêu đề cập nhật")
                .currentVersion(2)
                .build();

        when(documentDraftService.updateDraft(eq(1L), any(DocumentUpdateRequest.class), eq(100L)))
                .thenReturn(response);

        mockMvc.perform(put("/documents/1")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Tiêu đề cập nhật"))
                .andExpect(jsonPath("$.data.currentVersion").value(2));
    }

    @Test
    @DisplayName("API POST /documents/{id}/attachments - Tải tệp đính kèm thành công (UC09)")
    void uploadAttachment_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "%PDF-1.4".getBytes());

        DocumentAttachmentResponse response = DocumentAttachmentResponse.builder()
                .id(10L)
                .originalFileName("test.pdf")
                .isMainDocument(true)
                .build();

        when(documentAttachmentService.uploadAttachment(eq(1L), any(), anyBoolean(), eq(100L)))
                .thenReturn(response);

        mockMvc.perform(multipart("/documents/1/attachments")
                        .file(file)
                        .param("isMain", "true")
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.originalFileName").value("test.pdf"));
    }

    @Test
    @DisplayName("API DELETE /documents/{id}/attachments/{attachmentId} - Xóa tệp đính kèm (UC09)")
    void deleteAttachment_Success() throws Exception {
        mockMvc.perform(delete("/documents/1/attachments/10").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(documentAttachmentService).deleteAttachment(1L, 10L, 100L);
    }

    @Test
    @DisplayName("API GET /documents/{id}/versions - Lấy danh sách phiên bản (UC10)")
    void getVersions_Success() throws Exception {
        DocumentVersionResponse v1 = DocumentVersionResponse.builder().id(1L).versionNumber(1).build();
        when(documentVersionService.getVersions(1L)).thenReturn(List.of(v1));

        mockMvc.perform(get("/documents/1/versions").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].versionNumber").value(1));
    }

    @Test
    @DisplayName("API GET /documents/{id}/versions/compare - So sánh phiên bản (UC10)")
    void compareVersions_Success() throws Exception {
        VersionCompareResponse diff = VersionCompareResponse.builder()
                .fromVersionNumber(1)
                .toVersionNumber(2)
                .titleChanged(true)
                .oldTitle("Old")
                .newTitle("New")
                .build();

        when(documentVersionService.compareVersions(1L, 1, 2)).thenReturn(diff);

        mockMvc.perform(get("/documents/1/versions/compare?fromVersion=1&toVersion=2").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.titleChanged").value(true))
                .andExpect(jsonPath("$.data.newTitle").value("New"));
    }

    @Test
    @DisplayName("API POST /documents/{id}/versions/{versionNumber}/restore - Khôi phục phiên bản cũ (UC10)")
    void restoreVersion_Success() throws Exception {
        DocumentResponse doc = DocumentResponse.builder().id(1L).currentVersion(3).build();
        when(documentVersionService.restoreVersion(1L, 1, 100L)).thenReturn(doc);

        mockMvc.perform(post("/documents/1/versions/1/restore").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentVersion").value(3));
    }

    @Test
    @DisplayName("API GET /documents/{id}/eligible-approvers - Lấy danh sách người duyệt (UC11)")
    void getEligibleApprovers_Success() throws Exception {
        EligibleApproverResponse approver = EligibleApproverResponse.builder()
                .userId(200L)
                .username("dean")
                .fullName("Trưởng khoa CNTT")
                .build();

        when(documentApprovalService.getEligibleApprovers(1L, 100L)).thenReturn(List.of(approver));

        mockMvc.perform(get("/documents/1/eligible-approvers").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].userId").value(200))
                .andExpect(jsonPath("$.data[0].username").value("dean"));
    }

    @Test
    @DisplayName("API POST /documents/{id}/submit-approval - Gửi văn bản để duyệt (UC11)")
    void submitForApproval_Success() throws Exception {
        SubmitApprovalRequest request = SubmitApprovalRequest.builder()
                .approverUserId(200L)
                .submitNote("Kính gửi duyệt")
                .build();

        DocumentResponse doc = DocumentResponse.builder()
                .id(1L)
                .status(DocumentStatus.WAITING_APPROVAL)
                .build();

        when(documentApprovalService.submitForApproval(eq(1L), any(SubmitApprovalRequest.class), eq(100L)))
                .thenReturn(doc);

        mockMvc.perform(post("/documents/1/submit-approval")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("WAITING_APPROVAL"));
    }

    @Test
    @DisplayName("API POST /documents/{id}/recall-approval - Thu hồi yêu cầu duyệt (UC11)")
    void recallApproval_Success() throws Exception {
        DocumentResponse doc = DocumentResponse.builder()
                .id(1L)
                .status(DocumentStatus.DRAFT)
                .build();

        when(documentApprovalService.recallApproval(eq(1L), any(), eq(100L)))
                .thenReturn(doc);

        mockMvc.perform(post("/documents/1/recall-approval")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recallReason\": \"Cần sửa thêm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }
}
