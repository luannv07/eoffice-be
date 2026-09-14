package vn.edu.epu.eoffice_be.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class WebMvcMappingConfigTest {

    static class DocumentController {
        public void getDraftDetail() {}
        public void getDraftDetail(Long id) {}
        public void getDetail() {}
        public void otherMethod() {}
    }

    static class DocumentWorkflowController {
        public void getDetail() {}
        public void getDraftDetail() {}
    }

    static class DocumentControllerCustom {
        public void getDraftDetail() {}
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
    void isReviewedConflict_ShouldReturnFalse_WhenAnyParameterIsNull() throws Exception {
        RequestMappingInfo info = RequestMappingInfo.paths("/documents/{id}")
                .methods(RequestMethod.GET)
                .build();
        DocumentController handler = new DocumentController();
        Method method = DocumentController.class.getMethod("getDraftDetail");

        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(null, handler, method));
        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(info, null, method));
        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(info, handler, null));
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
    void isReviewedConflict_ShouldReturnFalseForCrossOrInvalidHandlerMethodPairs() throws Exception {
        RequestMappingInfo info = RequestMappingInfo.paths("/documents/{id}")
                .methods(RequestMethod.GET)
                .build();

        // DocumentController với method không được duyệt getDetail
        DocumentController handler1 = new DocumentController();
        Method methodWrong1 = DocumentController.class.getMethod("getDetail");
        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(info, handler1, methodWrong1));

        // DocumentWorkflowController với method không được duyệt getDraftDetail
        DocumentWorkflowController handler2 = new DocumentWorkflowController();
        Method methodWrong2 = DocumentWorkflowController.class.getMethod("getDraftDetail");
        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(info, handler2, methodWrong2));

        // Class có tiền tố trùng nhưng không phải class được duyệt
        DocumentControllerCustom prefixHandler = new DocumentControllerCustom();
        Method methodCustom = DocumentControllerCustom.class.getMethod("getDraftDetail");
        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(info, prefixHandler, methodCustom));

        // Controller không liên quan
        UnrelatedController unrelated = new UnrelatedController();
        Method methodUnrelated = UnrelatedController.class.getMethod("getDetail");
        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(info, unrelated, methodUnrelated));

        // Khác URL mapping
        RequestMappingInfo otherInfo = RequestMappingInfo.paths("/other/{id}")
                .methods(RequestMethod.GET)
                .build();
        Method methodDraft = DocumentController.class.getMethod("getDraftDetail");
        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(otherInfo, handler1, methodDraft));
    }

    @Test
    void isReviewedConflict_ShouldSupportOverloadedReviewedMethod() throws Exception {
        RequestMappingInfo info = RequestMappingInfo.paths("/documents/{id}")
                .methods(RequestMethod.GET)
                .build();

        DocumentController handler = new DocumentController();
        Method overloadedMethod = DocumentController.class.getMethod("getDraftDetail", Long.class);
        assertTrue(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(info, handler, overloadedMethod));
    }
}