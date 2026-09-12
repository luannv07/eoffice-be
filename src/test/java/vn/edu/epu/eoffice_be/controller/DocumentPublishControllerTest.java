package vn.edu.epu.eoffice_be.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.epu.eoffice_be.dto.PublishRequest;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.service.DocumentPublishService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        mockMvc = MockMvcBuilders.standaloneSetup(publishController).build();
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
    void testVerifyIntegrityEndpoint() throws Exception {
        when(publishService.verifyIntegrity(1L)).thenReturn(true);

        mockMvc.perform(get("/documents/1/verify-integrity"))
                .andExpect(status().isOk());
    }
    @Test
    void testVerifyIntegrityEndpoint_WhenFalse() throws Exception {
        when(publishService.verifyIntegrity(2L)).thenReturn(false);

        mockMvc.perform(get("/documents/2/verify-integrity"))
                .andExpect(status().isOk());
    }
}