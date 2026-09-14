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

        record AllowedHandlerMethod(String handlerFqn, String methodName) {
            boolean matches(String candidateFqn, String candidateMethodName) {
                return handlerFqn.equals(candidateFqn) && methodName.equals(candidateMethodName);
            }
        }

        record ReviewedConflictRule(
                org.springframework.web.bind.annotation.RequestMethod httpMethod,
                String pathPattern,
                AllowedHandlerMethod preferredHandler,
                AllowedHandlerMethod suppressedHandler
        ) {
            boolean matchesMapping(RequestMappingInfo mapping) {
                if (mapping == null) return false;
                if (mapping.getMethodsCondition() == null || !mapping.getMethodsCondition().getMethods().contains(httpMethod)) {
                    return false;
                }
                return mapping.getPatternValues() != null && mapping.getPatternValues().contains(pathPattern);
            }

            boolean matchesSuppressed(RequestMappingInfo mapping, Object handler, Method method) {
                if (!matchesMapping(mapping) || handler == null || method == null) return false;
                String handlerFqn = handler instanceof String s ? s : org.springframework.util.ClassUtils.getUserClass(handler).getName();
                String declaringFqn = method.getDeclaringClass().getName();
                String methodName = method.getName();
                return suppressedHandler.matches(handlerFqn, methodName) || suppressedHandler.matches(declaringFqn, methodName);
            }

            boolean matchesPreferred(Object handler, Method method) {
                if (handler == null || method == null) return false;
                String handlerFqn = handler instanceof String s ? s : org.springframework.util.ClassUtils.getUserClass(handler).getName();
                String declaringFqn = method.getDeclaringClass().getName();
                String methodName = method.getName();
                return preferredHandler.matches(handlerFqn, methodName) || preferredHandler.matches(declaringFqn, methodName);
            }
        }

        static final java.util.List<ReviewedConflictRule> REVIEWED_RULES = java.util.List.of(
                new ReviewedConflictRule(
                        org.springframework.web.bind.annotation.RequestMethod.GET,
                        "/documents/{id}",
                        new AllowedHandlerMethod("vn.edu.epu.eoffice_be.controller.DocumentController", "getDraftDetail"),
                        new AllowedHandlerMethod("vn.edu.epu.eoffice_be.controller.DocumentWorkflowController", "getDetail")
                )
        );

        @Override
        protected void registerHandlerMethod(Object handler, Method method, RequestMappingInfo mapping) {
            try {
                super.registerHandlerMethod(handler, method, mapping);
            } catch (IllegalStateException ex) {
                if (isAmbiguousMappingException(ex)) {
                    ReviewedConflictRule matchingRule = findMatchingRule(mapping);
                    if (matchingRule != null && matchingRule.matchesSuppressed(mapping, handler, method)) {
                        org.springframework.web.method.HandlerMethod existing = getHandlerMethods().get(mapping);
                        if (existing != null && matchingRule.matchesPreferred(existing.getBean(), existing.getMethod())) {
                            log.info("[AUDIT] Bỏ qua mapping trùng lặp đã được duyệt: {} cho {}.{} (ưu tiên handler đã đăng ký trước: {}.{})",
                                    mapping,
                                    method.getDeclaringClass().getName(),
                                    method.getName(),
                                    existing.getMethod().getDeclaringClass().getName(),
                                    existing.getMethod().getName());
                            return;
                        }
                    }
                }
                // Fail-fast cho tất cả các trường hợp xung đột chưa được phê duyệt hoặc sai thứ tự ưu tiên
                throw ex;
            }
        }

        static boolean isAmbiguousMappingException(IllegalStateException ex) {
            return ex != null && ex.getMessage() != null && ex.getMessage().startsWith("Ambiguous mapping.");
        }

        static ReviewedConflictRule findMatchingRule(RequestMappingInfo mapping) {
            if (mapping == null) return null;
            return REVIEWED_RULES.stream()
                    .filter(rule -> rule.matchesMapping(mapping))
                    .findFirst()
                    .orElse(null);
        }

        static boolean isReviewedConflict(RequestMappingInfo mapping, Object handler, Method method) {
            if (mapping == null || handler == null || method == null) {
                return false;
            }
            ReviewedConflictRule rule = findMatchingRule(mapping);
            if (rule == null) return false;
            return rule.matchesSuppressed(mapping, handler, method) || rule.matchesPreferred(handler, method);
        }
    }
}