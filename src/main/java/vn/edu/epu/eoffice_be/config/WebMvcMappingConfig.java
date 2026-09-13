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

    static class AmbiguousMappingSafeHandlerMapping extends RequestMappingHandlerMapping {

        /**
         * Danh sách các mapping trùng lặp đã qua kiểm duyệt (reviewed allowlist).
         * Chỉ những xung đột trong danh sách này mới được phép xử lý an toàn;
         * tất cả các xung đột khác sẽ fail-fast ngay khi khởi động ứng dụng.
         */
        static final java.util.Set<String> REVIEWED_ALLOWLIST = java.util.Set.of(
                "{GET [/documents/{id}]}"
        );

        @Override
        protected void registerHandlerMethod(Object handler, Method method, RequestMappingInfo mapping) {
            try {
                super.registerHandlerMethod(handler, method, mapping);
            } catch (IllegalStateException ex) {
                if (isAmbiguousMappingException(ex) && isReviewedConflict(mapping)) {
                    log.info("[AUDIT] Bỏ qua mapping trùng lặp đã được duyệt: {} cho method: {}.{} (ưu tiên handler đã đăng ký trước)",
                            mapping, handler.getClass().getSimpleName(), method.getName());
                    return;
                }
                // Fail-fast cho tất cả các trường hợp xung đột chưa được phê duyệt hoặc lỗi khác
                throw ex;
            }
        }

        static boolean isAmbiguousMappingException(IllegalStateException ex) {
            return ex != null && ex.getMessage() != null && ex.getMessage().contains("Ambiguous mapping");
        }

        static boolean isReviewedConflict(RequestMappingInfo mapping) {
            if (mapping == null) {
                return false;
            }
            String mappingStr = mapping.toString();
            return REVIEWED_ALLOWLIST.stream().anyMatch(mappingStr::contains);
        }
    }
}