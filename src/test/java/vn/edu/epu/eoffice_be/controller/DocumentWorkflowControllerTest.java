package vn.edu.epu.eoffice_be.controller;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.epu.eoffice_be.common.exception.GlobalExceptionHandler;
import vn.edu.epu.eoffice_be.dto.PendingDocumentResponse;
import vn.edu.epu.eoffice_be.dto.WorkflowTimelineResponse;
import vn.edu.epu.eoffice_be.service.DocumentWorkflowService;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
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
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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

        when(workflowService.getPendingDocuments(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/documents/pending-review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.number").value(0));

        verify(workflowService, times(1)).getPendingDocuments(any(Pageable.class));
    }

    @Test
    void testGetPendingReviewDocuments_EmptyPage_Success() throws Exception {
        Page<PendingDocumentResponse> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(5, 10), 0);

        when(workflowService.getPendingDocuments(any(Pageable.class))).thenReturn(emptyPage);

        mockMvc.perform(get("/documents/pending-review?page=5&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.number").value(5));

        verify(workflowService, times(1)).getPendingDocuments(any(Pageable.class));
    }

    @Test
    void testGetPendingReviewDocuments_CustomPageAndSize_Success() throws Exception {
        List<PendingDocumentResponse> docs = List.of(
                PendingDocumentResponse.builder().id(2L).title("Doc 2").build(),
                PendingDocumentResponse.builder().id(3L).title("Doc 3").build()
        );
        Page<PendingDocumentResponse> page = new PageImpl<>(docs, PageRequest.of(1, 2), 5);

        when(workflowService.getPendingDocuments(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/documents/pending-review?page=1&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.number").value(1));
    }

    @Test
    void testGetDocumentTimeline_Authorized_Returns200() throws Exception {
        WorkflowTimelineResponse timelineItem = WorkflowTimelineResponse.builder()
                .id(1L)
                .documentId(10L)
                .action("SUBMIT_APPROVAL")
                .comment("Kính gửi lãnh đạo phê duyệt")
                .build();

        when(workflowService.getDocumentTimeline(eq(10L), any())).thenReturn(List.of(timelineItem));

        mockMvc.perform(get("/documents/10/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].documentId").value(10L))
                .andExpect(jsonPath("$[0].action").value("SUBMIT_APPROVAL"));

        verify(workflowService, times(1)).getDocumentTimeline(eq(10L), any());
    }

    @Test
    void testGetDocumentTimeline_Unauthorized_Returns403() throws Exception {
        when(workflowService.getDocumentTimeline(eq(99L), any()))
                .thenThrow(new AccessDeniedException("User is not authorized to view timeline"));

        mockMvc.perform(get("/documents/99/timeline"))
                .andExpect(status().isForbidden());

        verify(workflowService, times(1)).getDocumentTimeline(eq(99L), any());
    }
}