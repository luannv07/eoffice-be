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
    void isAmbiguousMappingException_ShouldDetectStrictAmbiguousPrefix() {
        IllegalStateException ex1 = new IllegalStateException("Ambiguous mapping. Cannot map 'b' method...");
        assertTrue(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isAmbiguousMappingException(ex1));

        IllegalStateException ex2 = new IllegalStateException("Other error");
        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isAmbiguousMappingException(ex2));

        // Không bắt đầu bằng tiền tố chuẩn xác
        IllegalStateException ex3 = new IllegalStateException("Failed because of Ambiguous mapping in handler");
        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isAmbiguousMappingException(ex3));

        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isAmbiguousMappingException(null));
    }

    @Test
    void isReviewedConflict_ShouldReturnFalse_WhenAnyParameterIsNull() throws Exception {
        RequestMappingInfo info = RequestMappingInfo.paths("/documents/{id}")
                .methods(RequestMethod.GET)
                .build();
        Method method = vn.edu.epu.eoffice_be.controller.DocumentController.class.getMethod("getDraftDetail", Long.class, org.springframework.security.core.Authentication.class);

        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(null, "vn.edu.epu.eoffice_be.controller.DocumentController", method));
        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(info, null, method));
        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(info, "vn.edu.epu.eoffice_be.controller.DocumentController", null));
    }

    @Test
    void isReviewedConflict_ShouldReturnTrueForExactReviewedFqnHandlerAndMethod() throws Exception {
        RequestMappingInfo info = RequestMappingInfo.paths("/documents/{id}")
                .methods(RequestMethod.GET)
                .build();

        Method method1 = vn.edu.epu.eoffice_be.controller.DocumentController.class.getMethod("getDraftDetail", Long.class, org.springframework.security.core.Authentication.class);
        assertTrue(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(info, "vn.edu.epu.eoffice_be.controller.DocumentController", method1));

        Method method2 = vn.edu.epu.eoffice_be.controller.DocumentWorkflowController.class.getMethod("getDetail", Long.class);
        assertTrue(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(info, "vn.edu.epu.eoffice_be.controller.DocumentWorkflowController", method2));
    }

    @Test
    void isReviewedConflict_ShouldReturnFalseWhenSameSimpleNameHasDifferentPackageFqn() throws Exception {
        RequestMappingInfo info = RequestMappingInfo.paths("/documents/{id}")
                .methods(RequestMethod.GET)
                .build();

        // Lớp lồng DocumentController có cùng simple name nhưng FQN khác (khác package) -> không được khớp
        DocumentController fakeHandler = new DocumentController();
        Method fakeMethod = DocumentController.class.getMethod("getDraftDetail");
        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(info, fakeHandler, fakeMethod));

        DocumentWorkflowController fakeWorkflowHandler = new DocumentWorkflowController();
        Method fakeWorkflowMethod = DocumentWorkflowController.class.getMethod("getDetail");
        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(info, fakeWorkflowHandler, fakeWorkflowMethod));
    }

    @Test
    void isReviewedConflict_ShouldReturnFalseForCrossOrInvalidHandlerMethodPairs() throws Exception {
        RequestMappingInfo info = RequestMappingInfo.paths("/documents/{id}")
                .methods(RequestMethod.GET)
                .build();

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
        Method method1 = vn.edu.epu.eoffice_be.controller.DocumentController.class.getMethod("getDraftDetail", Long.class, org.springframework.security.core.Authentication.class);
        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(otherInfo, "vn.edu.epu.eoffice_be.controller.DocumentController", method1));
    }
}