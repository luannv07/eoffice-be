package vn.edu.epu.eoffice_be.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import static org.junit.jupiter.api.Assertions.*;

class WebMvcMappingConfigTest {

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
    void isReviewedConflict_ShouldMatchReviewedEndpoints() {
        RequestMappingInfo info = RequestMappingInfo.paths("/documents/{id}")
                .methods(org.springframework.web.bind.annotation.RequestMethod.GET)
                .build();

        assertTrue(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(info));

        RequestMappingInfo otherInfo = RequestMappingInfo.paths("/users/{id}")
                .methods(org.springframework.web.bind.annotation.RequestMethod.GET)
                .build();

        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(otherInfo));
        assertFalse(WebMvcMappingConfig.AmbiguousMappingSafeHandlerMapping.isReviewedConflict(null));
    }
}