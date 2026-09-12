package vn.edu.epu.eoffice_be.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.epu.eoffice_be.common.exception.GlobalExceptionHandler;
import vn.edu.epu.eoffice_be.dto.PublishRequest;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.service.DocumentPublishService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DocumentPublishControllerTest {

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
        Document doc = new Document();
        doc.setId(1L);
        doc.setStatus("PUBLISHED");

        when(publishService.publishDocument(eq(1L), any(PublishRequest.class))).thenReturn(doc);

        String rawJson = "{\"issueDate\":\"2026-09-12\",\"isPublicToAll\":true}";

        mockMvc.perform(post("/documents/1/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isOk());
    }

    @Test
    void testVerifyIntegrityEndpoint_Authorized_Returns200() throws Exception {
        lenient().when(publishService.verifyIntegrity(eq(1L), any())).thenReturn(true);
        lenient().when(publishService.verifyIntegrity(1L)).thenReturn(true);

        mockMvc.perform(get("/documents/1/verify-integrity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(1L))
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void testVerifyIntegrityEndpoint_WhenFalse_Returns200() throws Exception {
        lenient().when(publishService.verifyIntegrity(eq(2L), any())).thenReturn(false);
        lenient().when(publishService.verifyIntegrity(2L)).thenReturn(false);

        mockMvc.perform(get("/documents/2/verify-integrity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(2L))
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void testVerifyIntegrityEndpoint_Unauthorized_Returns403() throws Exception {
        lenient().when(publishService.verifyIntegrity(eq(99L), any()))
                .thenThrow(new AccessDeniedException("User is not authorized to verify integrity"));

        mockMvc.perform(get("/documents/99/verify-integrity"))
                .andExpect(status().isForbidden());
    }
}