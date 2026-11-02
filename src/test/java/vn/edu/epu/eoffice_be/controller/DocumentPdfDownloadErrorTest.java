package vn.edu.epu.eoffice_be.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.common.exception.GlobalExceptionHandler;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.service.DocumentQueryService;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DocumentPdfDownloadErrorTest {
    @org.mockito.Mock private vn.edu.epu.eoffice_be.service.DocumentReadPolicy draftReadPolicy;

    @Mock DocumentQueryService service;
    @InjectMocks DocumentQueryController controller;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {
            "DOCUMENT_PDF_NOT_FOUND", "DOCUMENT_PDF_STORAGE_UNAVAILABLE", "DOCUMENT_PDF_STORAGE_FAILED"
    })
    void downloadReturnsSpecificStorageError(ErrorCode code) throws Exception {
        var admin = new UsernamePasswordAuthenticationToken(
                new JwtUserPrincipal(1001L, "admin", "ADMIN", true, List.of()),
                null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(admin);
        when(service.resolveViewerRole(admin)).thenReturn(vn.edu.epu.eoffice_be.common.enums.Role.ADMIN);
        when(service.downloadDocumentForAdmin(5001L)).thenThrow(new BusinessException(code));
        var filter = new SecurityContextHolderAwareRequestFilter();
        filter.afterPropertiesSet();
        var mvc = MockMvcBuilders.standaloneSetup(controller).addFilters(filter)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/documents/5001/download").with(authentication(admin)))
                .andExpect(status().is(code.getStatus().value()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(code.getCode()))
                .andExpect(jsonPath("$.message").value(code.getMessage()));
    }
}
