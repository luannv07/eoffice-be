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
import vn.edu.epu.eoffice_be.common.enums.DocumentSecurityLevel;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.DocumentUrgencyLevel;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
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
                .setMessageConverters(converter, new org.springframework.http.converter.ResourceHttpMessageConverter())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        JwtUserPrincipal principal = new JwtUserPrincipal(100L, "author", "USER", true, List.of());
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
    @DisplayName("API GET /documents/drafts - Bị từ chối 400 khi tham số phân trang không hợp lệ")
    void searchDrafts_InvalidPagination_ReturnsBadRequest() throws Exception {
        // page < 0
        mockMvc.perform(get("/documents/drafts?page=-1").principal(auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // size = 0
        mockMvc.perform(get("/documents/drafts?size=0").principal(auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // size > 100
        mockMvc.perform(get("/documents/drafts?size=101").principal(auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("API POST /documents/drafts - Bị từ chối 400 khi độ dài trường vượt quá giới hạn")
    void createDraft_ExceededSize_ReturnsBadRequest() throws Exception {
        DocumentCreateRequest request = DocumentCreateRequest.builder()
                .title("A".repeat(501)) // vượt quá 500
                .summary("Trích yếu")
                .documentTypeCd("QUYET_DINH")
                .issuingOrgNodeId(10L)
                .build();

        mockMvc.perform(post("/documents/drafts")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        DocumentCreateRequest typeTooLong = DocumentCreateRequest.builder()
                .title("Tiêu đề hợp lệ")
                .summary("Trích yếu")
                .documentTypeCd("A".repeat(51)) // vượt quá 50
                .issuingOrgNodeId(10L)
                .build();

        mockMvc.perform(post("/documents/drafts")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(typeTooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("API POST /documents/drafts - Bị từ chối 400 khi issuingOrgNodeId <= 0")
    void createDraft_NonPositiveIssuingOrgNodeId_ReturnsBadRequest() throws Exception {
        String jsonZero = "{\"title\":\"Tiêu đề\",\"summary\":\"Trích yếu\",\"documentTypeCd\":\"QD\",\"issuingOrgNodeId\":0}";
        mockMvc.perform(post("/documents/drafts")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonZero))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        String jsonNegative = "{\"title\":\"Tiêu đề\",\"summary\":\"Trích yếu\",\"documentTypeCd\":\"QD\",\"issuingOrgNodeId\":-5}";
        mockMvc.perform(post("/documents/drafts")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonNegative))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("API POST /documents/drafts - Bị từ chối 400 khi gửi securityLevel hoặc urgencyLevel là null")
    void createDraft_NullSecurityOrUrgencyLevel_ReturnsBadRequest() throws Exception {
        String jsonNullSecurity = "{\"title\":\"Tiêu đề\",\"summary\":\"Trích yếu\",\"documentTypeCd\":\"QD\",\"issuingOrgNodeId\":10,\"securityLevel\":null}";
        mockMvc.perform(post("/documents/drafts")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonNullSecurity))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        String jsonNullUrgency = "{\"title\":\"Tiêu đề\",\"summary\":\"Trích yếu\",\"documentTypeCd\":\"QD\",\"issuingOrgNodeId\":10,\"urgencyLevel\":null}";
        mockMvc.perform(post("/documents/drafts")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonNullUrgency))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
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
        when(documentVersionService.getVersions(1L, 100L)).thenReturn(List.of(v1));

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

        when(documentVersionService.compareVersions(1L, 1, 2, 100L)).thenReturn(diff);

        mockMvc.perform(get("/documents/1/versions/compare?fromVersion=1&toVersion=2").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.titleChanged").value(true))
                .andExpect(jsonPath("$.data.newTitle").value("New"));
    }

    @Test
    @DisplayName("API - Trả về 401 khi không có Authentication hoặc Principal không hợp lệ")
    void unauthenticatedAccess_ThrowsUnauthorized() throws Exception {
        mockMvc.perform(get("/documents/1/versions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
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

    @Test
    @DisplayName("API GET /documents/drafts - Bị từ chối 400 khi từ khóa tìm kiếm vượt quá 255 ký tự")
    void searchDrafts_KeywordTooLong_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/documents/drafts?keyword=" + "A".repeat(256)).principal(auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("API POST /documents/{id}/recall-approval - Bị từ chối 400 khi lý do thu hồi vượt quá 500 ký tự")
    void recallApproval_ReasonTooLong_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/documents/1/recall-approval")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recallReason\": \"" + "A".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Kiểm tra Jackson Deserialization áp dụng đúng giá trị mặc định cho DocumentUpdateRequest khi bỏ trống")
    void documentUpdateRequest_DeserializationDefaults() throws Exception {
        String json = "{\"title\":\"Tiêu đề\",\"summary\":\"Trích yếu\",\"documentTypeCd\":\"QD\",\"issuingOrgNodeId\":10}";
        DocumentUpdateRequest req = objectMapper.readValue(json, DocumentUpdateRequest.class);

        org.junit.jupiter.api.Assertions.assertTrue(req.isSaveNewVersion());
        org.junit.jupiter.api.Assertions.assertNull(req.getSecurityLevel());
        org.junit.jupiter.api.Assertions.assertNull(req.getUrgencyLevel());
    }

    @Test
    @DisplayName("API PUT /documents/{id} - Bị từ chối 400 khi changeSummary vượt quá 1000 ký tự")
    void updateDraft_ChangeSummaryTooLong_ReturnsBadRequest() throws Exception {
        String json = "{\"title\":\"Tiêu đề\",\"summary\":\"Trích yếu\",\"documentTypeCd\":\"QD\",\"issuingOrgNodeId\":10,\"changeSummary\":\"" + "A".repeat(1001) + "\"}";
        mockMvc.perform(put("/documents/1")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("API POST /documents/{id}/recall-approval - Bị từ chối 400 khi recallReason rỗng hoặc khoảng trắng")
    void recallApproval_BlankReason_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/documents/1/recall-approval")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recallReason\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/documents/1/recall-approval")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recallReason\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("API POST /documents/{id}/submit-approval - Bị từ chối 400 khi submitNote vượt quá 1000 ký tự")
    void submitApproval_NoteTooLong_ReturnsBadRequest() throws Exception {
        String json = "{\"approverUserId\": 1002, \"submitNote\": \"" + "A".repeat(1001) + "\"}";
        mockMvc.perform(post("/documents/1/submit-approval")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Kiểm tra Jackson Serialization của DocumentAttachmentResponse giữ đúng tên isMainDocument")
    void documentAttachmentResponse_Serialization_IncludesIsMainDocument() throws Exception {
        DocumentAttachmentResponse resp = DocumentAttachmentResponse.builder()
                .id(1L)
                .isMainDocument(true)
                .build();
        String json = objectMapper.writeValueAsString(resp);
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"isMainDocument\":true"));
    }

    @Test
    @DisplayName("Kiểm tra Jackson Deserialization áp dụng đúng giá trị mặc định cho DocumentCreateRequest khi bỏ trống")
    void documentCreateRequest_DeserializationDefaults() throws Exception {
        String json = "{\"title\":\"Tiêu đề\",\"summary\":\"Trích yếu\",\"documentTypeCd\":\"QD\",\"issuingOrgNodeId\":10}";
        DocumentCreateRequest req = objectMapper.readValue(json, DocumentCreateRequest.class);

        org.junit.jupiter.api.Assertions.assertEquals(DocumentSecurityLevel.NORMAL, req.getSecurityLevel());
        org.junit.jupiter.api.Assertions.assertEquals(DocumentUrgencyLevel.NORMAL, req.getUrgencyLevel());
    }

    @Test
    @DisplayName("API GET /documents/{id}/attachments/{attachmentId}/download - Tải tệp thành công")
    void downloadAttachment_Success() throws Exception {
        Long docId = 1L;
        Long attId = 10L;

        org.springframework.core.io.Resource mockResource =
                new org.springframework.core.io.ByteArrayResource("dummy file content".getBytes());
        vn.edu.epu.eoffice_be.entity.DocumentAttachment meta =
                vn.edu.epu.eoffice_be.entity.DocumentAttachment.builder()
                        .originalFileName("kehoach.docx")
                        .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        .build();

        when(documentAttachmentService.downloadAttachment(docId, attId, 100L)).thenReturn(mockResource);
        when(documentAttachmentService.getAttachmentMetadata(docId, attId, 100L)).thenReturn(meta);

        mockMvc.perform(get("/documents/1/attachments/10/download").principal(auth))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(
                        org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("kehoach.docx")));
    }

    @Test
    @DisplayName("API GET download attachment - Fallback khi originalFileName null và mimeType không hợp lệ")
    void downloadAttachment_FallbackWhenInvalidMimeOrNullName() throws Exception {
        Long docId = 1L;
        Long attId = 10L;

        org.springframework.core.io.Resource mockResource =
                new org.springframework.core.io.ByteArrayResource("dummy file content".getBytes());
        vn.edu.epu.eoffice_be.entity.DocumentAttachment meta =
                vn.edu.epu.eoffice_be.entity.DocumentAttachment.builder()
                        .originalFileName(null)
                        .mimeType("invalid/mime type with spaces")
                        .build();

        when(documentAttachmentService.downloadAttachment(docId, attId, 100L)).thenReturn(mockResource);
        when(documentAttachmentService.getAttachmentMetadata(docId, attId, 100L)).thenReturn(meta);

        mockMvc.perform(get("/documents/1/attachments/10/download").principal(auth))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(
                        org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("file")));
    }

    @Test
    @DisplayName("API GET download attachment - Bị từ chối 403 khi truy cập tệp chéo không có quyền")
    void downloadAttachment_Forbidden_WhenCrossUserAccess() throws Exception {
        Long docId = 1L;
        Long attId = 10L;

        when(documentAttachmentService.downloadAttachment(docId, attId, 100L))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền truy cập tệp đính kèm của văn bản này"));

        mockMvc.perform(get("/documents/1/attachments/10/download").principal(auth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("API GET download attachment - Bị từ chối 403 khi lấy metadata tệp chéo không có quyền")
    void downloadAttachment_Forbidden_WhenCrossUserMetadataAccess() throws Exception {
        Long docId = 1L;
        Long attId = 10L;

        org.springframework.core.io.Resource mockResource =
                new org.springframework.core.io.ByteArrayResource("dummy file content".getBytes());
        when(documentAttachmentService.downloadAttachment(docId, attId, 100L)).thenReturn(mockResource);
        when(documentAttachmentService.getAttachmentMetadata(docId, attId, 100L))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền truy cập tệp đính kèm của văn bản này"));

        mockMvc.perform(get("/documents/1/attachments/10/download").principal(auth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("API Bị từ chối 401 khi không có thông tin Authentication")
    void api_Unauthorized_WhenNoAuth() throws Exception {
        mockMvc.perform(get("/documents/1/versions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
