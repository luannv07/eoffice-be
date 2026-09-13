package vn.edu.epu.eoffice_be.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

/**
 * Cấu hình tùy chỉnh RequestMappingHandlerMapping để giải quyết xung đột mapping trùng lặp
 * giữa các controller khi hợp nhất các module (ví dụ: GET /documents/{id}).
 */
@Slf4j
@Configuration
public class WebMvcMappingConfig implements WebMvcRegistrations {

    @Override
    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        return new AmbiguousMappingSafeHandlerMapping();
    }

    private static class AmbiguousMappingSafeHandlerMapping extends RequestMappingHandlerMapping {

        @Override
        protected void registerHandlerMethod(Object handler, Method method, RequestMappingInfo mapping) {
            try {
                super.registerHandlerMethod(handler, method, mapping);
            } catch (IllegalStateException ex) {
                if (ex.getMessage() != null && ex.getMessage().contains("Ambiguous mapping")) {
                    log.warn("Bỏ qua mapping trùng lặp: {} cho method: {}.{} (đã được ánh xạ bởi controller khác)",
                            mapping, handler.getClass().getSimpleName(), method.getName());
                } else {
                    throw ex;
                }
            }
        }
    }
}