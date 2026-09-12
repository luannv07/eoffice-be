package vn.edu.epu.eoffice_be.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.epu.eoffice_be.service.DocumentWorkflowService;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void testGetPendingReviewDocuments() throws Exception {
        when(workflowService.getPendingDocuments()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/documents/pending-review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").exists());

        verify(workflowService, atLeastOnce()).getPendingDocuments();
    }

    @Test
    void testGetDocumentTimeline() throws Exception {
        when(workflowService.getDocumentTimeline(anyLong())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/documents/1/timeline"))
                .andExpect(status().isOk());

        verify(workflowService, atLeastOnce()).getDocumentTimeline(1L);
    }
}