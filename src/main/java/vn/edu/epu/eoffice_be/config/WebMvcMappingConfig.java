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

        record ReviewedConflictRule(
                org.springframework.web.bind.annotation.RequestMethod httpMethod,
                String pathPattern,
                java.util.Set<String> allowedHandlerSimpleNames,
                java.util.Set<String> allowedMethodNames
        ) {
            boolean matches(RequestMappingInfo mapping, Object handler, Method method) {
                if (mapping == null || method == null) {
                    return false;
                }
                boolean methodMatches = mapping.getMethodsCondition().getMethods().contains(httpMethod);
                boolean pathMatches = mapping.getPatternValues().contains(pathPattern);

                String declaringClassName = method.getDeclaringClass().getSimpleName();
                boolean handlerMatches = allowedHandlerSimpleNames.contains(declaringClassName);
                if (!handlerMatches && handler != null) {
                    if (handler instanceof String s) {
                        handlerMatches = allowedHandlerSimpleNames.stream().anyMatch(name -> name.equalsIgnoreCase(s));
                    } else {
                        String simpleName = handler.getClass().getSimpleName();
                        handlerMatches = allowedHandlerSimpleNames.stream().anyMatch(simpleName::startsWith);
                    }
                }

                boolean actionMethodMatches = allowedMethodNames.contains(method.getName());

                return methodMatches && pathMatches && handlerMatches && actionMethodMatches;
            }
        }

        static final java.util.List<ReviewedConflictRule> REVIEWED_RULES = java.util.List.of(
                new ReviewedConflictRule(
                        org.springframework.web.bind.annotation.RequestMethod.GET,
                        "/documents/{id}",
                        java.util.Set.of("DocumentController", "DocumentWorkflowController"),
                        java.util.Set.of("getDraftDetail", "getDetail")
                )
        );

        @Override
        protected void registerHandlerMethod(Object handler, Method method, RequestMappingInfo mapping) {
            try {
                super.registerHandlerMethod(handler, method, mapping);
            } catch (IllegalStateException ex) {
                if (isAmbiguousMappingException(ex) && isReviewedConflict(mapping, handler, method)) {
                    log.info("[AUDIT] Bỏ qua mapping trùng lặp đã được duyệt: {} cho {}.{} (ưu tiên handler đã đăng ký trước)",
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

        static boolean isReviewedConflict(RequestMappingInfo mapping, Object handler, Method method) {
            return REVIEWED_RULES.stream().anyMatch(rule -> rule.matches(mapping, handler, method));
        }
    }
}