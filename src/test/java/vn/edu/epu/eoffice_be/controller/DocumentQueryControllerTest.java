package vn.edu.epu.eoffice_be.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import vn.edu.epu.eoffice_be.service.DocumentQueryService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class DocumentQueryControllerTest {

    @Mock
    private DocumentQueryService documentQueryService;

    @InjectMocks
    private DocumentQueryController documentQueryController;

    @Test
    void testGetPublishedDocuments_Success() {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn("1");
        lenient().when(documentQueryService.getPublishedDocuments(any())).thenReturn(List.of());

        var response = documentQueryController.getPublishedDocuments(auth);
        assertNotNull(response);
    }
}