package vn.edu.epu.eoffice_be.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.common.exception.GlobalExceptionHandler;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.service.DocumentAttachmentService;

import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DocumentPdfFilesControllerTest {
    @Mock DocumentAttachmentService files;
    @InjectMocks DocumentController controller;
    MockMvc mvc;
    Authentication auth;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        auth = new UsernamePasswordAuthenticationToken(new JwtUserPrincipal(100L, "tester", "USER", true, List.of()), null, List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "official"})
    void servesRequestedPdfInline(String kind) throws Exception {
        byte[] bytes = {1, 2, 3, 4};
        var pdf = new ByteArrayResource(bytes);
        if (kind.equals("primary")) when(files.downloadPrimaryPdf(5001L, 100L)).thenReturn(pdf);
        else when(files.downloadOfficialPdf(5001L, 100L)).thenReturn(pdf);

        mvc.perform(get("/documents/5001/files/" + kind).principal(auth))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(bytes))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"document_5001_" + kind + ".pdf\""))
                .andExpect(header().string("Cache-Control", "no-store"));
        if (kind.equals("primary")) verify(files).downloadPrimaryPdf(5001L, 100L);
        else verify(files).downloadOfficialPdf(5001L, 100L);
        verifyNoMoreInteractions(files);
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "official"})
    void unauthenticatedRequestDoesNotReachService(String kind) throws Exception {
        mvc.perform(get("/documents/5001/files/" + kind)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
        verifyNoInteractions(files);
    }

    @ParameterizedTest
    @MethodSource("errors")
    void reportsSpecificErrorInsteadOfPdf(String kind, ErrorCode code) throws Exception {
        if (kind.equals("primary")) when(files.downloadPrimaryPdf(5001L, 100L)).thenThrow(new BusinessException(code));
        else when(files.downloadOfficialPdf(5001L, 100L)).thenThrow(new BusinessException(code));

        mvc.perform(get("/documents/5001/files/" + kind).principal(auth))
                .andExpect(status().is(code.getStatus().value()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(code.getCode()))
                .andExpect(jsonPath("$.message").value(code.getMessage()));
    }

    static Stream<Arguments> errors() {
        return Stream.of("primary", "official").flatMap(kind -> Stream.of(ErrorCode.FORBIDDEN,
                ErrorCode.DOCUMENT_PDF_NOT_FOUND, ErrorCode.DOCUMENT_PDF_STORAGE_UNAVAILABLE,
                ErrorCode.DOCUMENT_PDF_STORAGE_FAILED).map(code -> Arguments.of(kind, code)));
    }
}
