package vn.edu.epu.eoffice_be.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.security.OrgAwarePrincipal;
import vn.edu.epu.eoffice_be.service.DocumentQueryService;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("isAuthenticated()")
public class DocumentQueryController {

    /**
     * Cache for reflection lookup of getOrgId() method.
     * Uses ClassValue so that cached Method references do not strongly reference
     * the Class keys from a static map, allowing dynamic/proxy classes and their
     * ClassLoaders to be naturally garbage-collected without memory leaks or arbitrary cache clearing.
     */
    private static final ClassValue<Optional<Method>> ORG_ID_METHOD_CACHE = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            try {
                Method m = type.getMethod("getOrgId");
                return Optional.of(m);
            } catch (NoSuchMethodException e) {
                log.debug("No public getOrgId() found on class {}", type.getName());
                return Optional.empty();
            }
        }
    };

    /**
     * Annotation indicating package-private visibility intended strictly for testing purposes.
     */
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
    @interface VisibleForTesting {}

    private static final java.util.Set<String> DEFAULT_ALLOWED_LEGACY_PRINCIPALS = java.util.Set.of(
            vn.edu.epu.eoffice_be.security.JwtUserPrincipal.class.getName()
    );

    private final java.util.concurrent.atomic.AtomicReference<java.util.Set<String>> allowedLegacyPrincipals =
            new java.util.concurrent.atomic.AtomicReference<>(DEFAULT_ALLOWED_LEGACY_PRINCIPALS);

    private static boolean isRunningUnderTest() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().startsWith("org.junit.") || element.getClassName().contains("Test")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Package-private hook for registering allowed legacy migration principals in tests.
     */
    @VisibleForTesting
    void registerAllowedLegacyPrincipal(Class<?> clazz) {
        if (!isRunningUnderTest() && !Boolean.getBoolean("eoffice.allow-legacy-principal-registration")) {
            throw new IllegalStateException("registerAllowedLegacyPrincipal chỉ được phép gọi trong môi trường kiểm thử");
        }
        if (clazz == null) {
            throw new IllegalArgumentException("Class đăng ký không được null");
        }
        try {
            clazz.getMethod("getOrgId");
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class " + clazz.getName() + " phải khai báo phương thức public getOrgId()");
        }
        allowedLegacyPrincipals.updateAndGet(current -> {
            java.util.Set<String> copy = new java.util.HashSet<>(current);
            copy.add(clazz.getName());
            return java.util.Collections.unmodifiableSet(copy);
        });
    }

    /**
     * Package-private hook for restoring the allowed legacy migration principals to default state atomically.
     */
    @VisibleForTesting
    void resetAllowedLegacyPrincipals() {
        allowedLegacyPrincipals.set(DEFAULT_ALLOWED_LEGACY_PRINCIPALS);
    }

    DocumentQueryService queryService;

    private Long resolveUserOrgId(Authentication authentication) {
        if (authentication == null 
                || !authentication.isAuthenticated() 
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken 
                || authentication.getPrincipal() == null) {
            throw new AuthenticationCredentialsNotFoundException("Yêu cầu xác thực người dùng");
        }
        Object principal = authentication.getPrincipal();

        if (principal instanceof OrgAwarePrincipal orgAwarePrincipal) {
            Long orgId = orgAwarePrincipal.getOrgId();
            if (orgId != null) {
                return orgId;
            }
            throw new AccessDeniedException("Người dùng không có thông tin đơn vị (orgId) hợp lệ");
        }

        // Restrict legacy reflection only to audited/allowed classes
        if (!allowedLegacyPrincipals.get().contains(principal.getClass().getName())) {
            log.warn("Principal class {} is not an OrgAwarePrincipal and not in allowed legacy allowlist", principal.getClass().getName());
            throw new AccessDeniedException("Không thể xác định đơn vị (orgId) hợp lệ của người dùng");
        }

        Optional<Method> methodOpt = findOrgIdMethod(principal.getClass());
        if (methodOpt.isEmpty()) {
            log.warn("Principal class {} does not define a public getOrgId() method", principal.getClass().getName());
            throw new AccessDeniedException("Không thể xác định đơn vị (orgId) hợp lệ của người dùng");
        }

        Method method = methodOpt.get();
        Class<?> returnType = method.getReturnType();
        if (!Long.class.equals(returnType) && !long.class.equals(returnType)) {
            log.warn("Method getOrgId() on {} does not return Long or long (found: {})", principal.getClass().getName(), returnType.getName());
            throw new AccessDeniedException("Không thể xác định đơn vị (orgId) hợp lệ của người dùng");
        }

        try {
            Object val = method.invoke(principal);
            if (val instanceof Number num) {
                return num.longValue();
            }
        } catch (IllegalAccessException e) {
            log.warn("Cannot access getOrgId() on principal class {}: {}", principal.getClass().getName(), e.getMessage());
            throw new AccessDeniedException("Không thể xác định đơn vị (orgId) hợp lệ của người dùng", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Exception thrown while invoking getOrgId() on principal class {}",
                    principal.getClass().getName(), cause);
            if (cause instanceof Error err) {
                throw err;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AccessDeniedException("Không thể xác định đơn vị (orgId) hợp lệ của người dùng", cause);
        }

        throw new AccessDeniedException("Người dùng không có thông tin đơn vị (orgId) hợp lệ");
    }

    private Optional<Method> findOrgIdMethod(Class<?> clazz) {
        return ORG_ID_METHOD_CACHE.get(clazz);
    }

    private static final String ROLE_PREFIX = "ROLE_";

    private boolean isAdmin(Authentication authentication) {
        return hasAnyRole(authentication, Role.ADMIN, Role.SYSTEM_ADMIN);
    }

    private boolean isPrivilegedStaff(Authentication authentication) {
        return hasAnyRole(authentication, Role.LEADER, Role.CLERK);
    }

    private boolean canViewAllPublishedDocuments(Authentication authentication) {
        return isAdmin(authentication) || isPrivilegedStaff(authentication);
    }

    private boolean hasAnyRole(Authentication authentication, Role... roles) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getAuthorities() == null) {
            return false;
        }
        Set<String> targetRoles = Arrays.stream(roles)
                .flatMap(r -> Stream.of(r.name(), ROLE_PREFIX + r.name()))
                .collect(Collectors.toSet());

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> targetRoles.stream().anyMatch(tr -> tr.equalsIgnoreCase(auth)));
    }

    // UC16.1: Tra cứu danh sách văn bản ban hành (BE-04: hỗ trợ lọc theo documentBookId)
    @GetMapping("/published")
    public ResponseEntity<Object> getPublishedDocuments(
            @RequestParam(required = false) Long documentBookId,
            Authentication authentication) {
        if (canViewAllPublishedDocuments(authentication)) {
            if (documentBookId != null) {
                return ResponseEntity.ok(queryService.getAllPublishedDocuments(documentBookId));
            }
            return ResponseEntity.ok(queryService.getAllPublishedDocuments());
        }
        Long orgId = resolveUserOrgId(authentication);
        if (documentBookId != null) {
            return ResponseEntity.ok(queryService.getPublishedDocuments(orgId, documentBookId));
        }
        return ResponseEntity.ok(queryService.getPublishedDocuments(orgId));
    }

    // UC16.2: Xem chi tiết trích yếu
    @GetMapping("/{id}/details")
    public ResponseEntity<Object> getDocumentDetails(@PathVariable Long id, Authentication authentication) {
        if (isAdmin(authentication)) {
            return ResponseEntity.ok(queryService.getDocumentDetailsForAdmin(id));
        }
        if (isPrivilegedStaff(authentication)) {
            return ResponseEntity.ok(queryService.getDocumentDetailsForStaff(id));
        }
        Long orgId = resolveUserOrgId(authentication);
        return ResponseEntity.ok(queryService.getDocumentDetails(id, orgId));
    }

    // UC16.3: Tải file PDF chính thức
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id, Authentication authentication) {
        byte[] pdfBytes;
        if (isAdmin(authentication)) {
            pdfBytes = queryService.downloadDocumentForAdmin(id);
        } else if (isPrivilegedStaff(authentication)) {
            pdfBytes = queryService.downloadDocumentForStaff(id);
        } else {
            Long orgId = resolveUserOrgId(authentication);
            pdfBytes = queryService.downloadDocument(id, orgId);
        }
        ByteArrayResource resource = new ByteArrayResource(pdfBytes);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document_" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfBytes.length)
                .body(resource);
    }
}