package vn.edu.epu.eoffice_be.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class WebMvcMappingConfigTest {

    static class DocumentController {
        public void getDraftDetail() {}
        public void otherMethod() {}
    }

    static class DocumentWorkflowController {
        public void getDetail() {}
    }

    static class UnrelatedController {
        public void getDetail() {}
    }

    @Test
    void getRequestMappingHandlerMapping_ShouldReturnCustomInstance() {
        WebMvcMappingConfig config = new WebMvcMappingConfig();
        var mapping = config.getRequestMappingHandlerMapping();
        assertNotNull(mapping);
        assertInstanceOf(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.class, mapping);
    }

    @Test
    void isAmbiguousMappingException_ShouldDetectAmbiguousMessage() {
        IllegalStateException ex1 = new IllegalStateException("Ambiguous mapping. Cannot map 'b' method...");
        assertTrue(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isAmbiguousMappingException(ex1));

        IllegalStateException ex2 = new IllegalStateException("Other error");
        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isAmbiguousMappingException(ex2));

        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isAmbiguousMappingException(null));
    }

    @Test
    void isReviewedConflict_ShouldReturnTrueForExactReviewedHandlerAndMethod() throws Exception {
        RequestMappingInfo info = RequestMappingInfo.paths("/documents/{id}")
                .methods(RequestMethod.GET)
                .build();

        DocumentController handler1 = new DocumentController();
        Method method1 = DocumentController.class.getMethod("getDraftDetail");
        assertTrue(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(info, handler1, method1));

        DocumentWorkflowController handler2 = new DocumentWorkflowController();
        Method method2 = DocumentWorkflowController.class.getMethod("getDetail");
        assertTrue(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(info, handler2, method2));
    }

    @Test
    void isReviewedConflict_ShouldReturnFalseForUnreviewedHandlerOrMethod() throws Exception {
        RequestMappingInfo info = RequestMappingInfo.paths("/documents/{id}")
                .methods(RequestMethod.GET)
                .build();

        // Cùng mapping nhưng handler lạ
        UnrelatedController unapprovedHandler = new UnrelatedController();
        Method method1 = UnrelatedController.class.getMethod("getDetail");
        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(info, unapprovedHandler, method1));

        // Đúng handler nhưng method khác
        DocumentController approvedHandler = new DocumentController();
        Method method2 = DocumentController.class.getMethod("otherMethod");
        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(info, approvedHandler, method2));

        // Khác URL mapping
        RequestMappingInfo otherInfo = RequestMappingInfo.paths("/other/{id}")
                .methods(RequestMethod.GET)
                .build();
        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(otherInfo, approvedHandler, DocumentController.class.getMethod("getDraftDetail")));
    }
}