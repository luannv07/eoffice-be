package vn.edu.epu.eoffice_be.controller;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.common.exception.GlobalExceptionHandler;
import vn.edu.epu.eoffice_be.service.DocumentPublishService;
import vn.edu.epu.eoffice_be.service.DocumentWorkflowService;

import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DocumentPdfVerificationErrorTest {
    @Mock DocumentPublishService publishing;
    @Mock DocumentWorkflowService workflow;

    @ParameterizedTest
    @MethodSource("errors")
    void storageFailureReturnsSpecificErrorInsteadOfVerificationResult(String path, ErrorCode code) throws Exception {
        BusinessException failure = new BusinessException(code);
        if (path.equals("verify-integrity")) when(publishing.verifyIntegrity(eq(5001L), any())).thenThrow(failure);
        else when(workflow.verifyDocumentSignatures(5001L)).thenThrow(failure);
        var mvc = MockMvcBuilders.standaloneSetup(new DocumentPublishController(publishing), new DocumentWorkflowController(workflow))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var auth = new UsernamePasswordAuthenticationToken("tester", null, List.of());

        mvc.perform(get("/documents/5001/" + path).principal(auth))
                .andExpect(status().is(code.getStatus().value()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(code.getCode()))
                .andExpect(jsonPath("$.message").value(code.getMessage()));
    }

    static Stream<Arguments> errors() {
        return Stream.of("verify-integrity", "signatures/verify").flatMap(path -> Stream.of(
                ErrorCode.DOCUMENT_PDF_NOT_FOUND, ErrorCode.DOCUMENT_PDF_STORAGE_UNAVAILABLE,
                ErrorCode.DOCUMENT_PDF_STORAGE_FAILED, ErrorCode.DOCUMENT_PDF_REFERENCE_INVALID)
                .map(code -> Arguments.of(path, code)));
    }
}
