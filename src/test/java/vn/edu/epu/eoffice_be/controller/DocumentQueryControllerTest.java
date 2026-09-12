package vn.edu.epu.eoffice_be.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.epu.eoffice_be.service.DocumentQueryService;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DocumentQueryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DocumentQueryService documentQueryService;

    @InjectMocks
    private DocumentQueryController documentQueryController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(documentQueryController).build();
    }

    @Test
    void testGetDocuments_QueryEndpoints() throws Exception {
        lenient().when(documentQueryService.getPublishedDocuments(anyLong()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/documents/published")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }
}