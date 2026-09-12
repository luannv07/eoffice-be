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
import org.springframework.web.bind.annotation.*;
import vn.edu.epu.eoffice_be.security.OrgAwarePrincipal;
import vn.edu.epu.eoffice_be.service.DocumentQueryService;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

@Slf4j
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("isAuthenticated()")
public class DocumentQueryController {

    /**
     * Cache for reflection lookup of getOrgId() method.
     * Uses WeakHashMap wrapped in synchronizedMap so that dynamic/proxy classes
     * and their ClassLoaders can be garbage-collected without leaking memory.
     * A maximum bounded size is also enforced to prevent memory exhaustion.
     */
    private static final int MAX_CACHE_SIZE = 256;
    private static final Map<Class<?>, Optional<Method>> ORG_ID_METHOD_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>() {
                @Override
                public Optional<Method> put(Class<?> key, Optional<Method> value) {
                    if (size() >= MAX_CACHE_SIZE) {
                        clear();
                    }
                    return super.put(key, value);
                }
            });

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

        Optional<Method> methodOpt = findOrgIdMethod(principal.getClass());
        if (methodOpt.isEmpty()) {
            log.warn("Principal class {} does not define a public getOrgId() method", principal.getClass().getName());
            throw new AccessDeniedException("Không thể xác định đơn vị (orgId) hợp lệ của người dùng");
        }

        try {
            Object val = methodOpt.get().invoke(principal);
            if (val instanceof Long longVal) {
                return longVal;
            }
        } catch (IllegalAccessException e) {
            log.warn("Cannot access getOrgId() on principal class {}: {}", principal.getClass().getName(), e.getMessage());
            throw new AccessDeniedException("Không thể xác định đơn vị (orgId) hợp lệ của người dùng", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            log.warn("Exception thrown while invoking getOrgId() on principal class {}: {}",
                    principal.getClass().getName(), cause != null ? cause.getMessage() : e.getMessage());
            throw new AccessDeniedException("Không thể xác định đơn vị (orgId) hợp lệ của người dùng", e);
        }

        throw new AccessDeniedException("Người dùng không có thông tin đơn vị (orgId) hợp lệ");
    }

    private Optional<Method> findOrgIdMethod(Class<?> clazz) {
        synchronized (ORG_ID_METHOD_CACHE) {
            return ORG_ID_METHOD_CACHE.computeIfAbsent(clazz, c -> {
                try {
                    Method m = c.getMethod("getOrgId");
                    return Optional.of(m);
                } catch (NoSuchMethodException e) {
                    log.debug("No public getOrgId() found on class {}", c.getName());
                    return Optional.empty();
                }
            });
        }
    }

    // UC16.1: Tra cứu danh sách văn bản ban hành
    @GetMapping("/published")
    public ResponseEntity<Object> getPublishedDocuments(Authentication authentication) {
        Long orgId = resolveUserOrgId(authentication);
        return ResponseEntity.ok(queryService.getPublishedDocuments(orgId));
    }

    // UC16.2: Xem chi tiết trích yếu
    @GetMapping("/{id}/details")
    public ResponseEntity<Object> getDocumentDetails(@PathVariable Long id, Authentication authentication) {
        Long orgId = resolveUserOrgId(authentication);
        return ResponseEntity.ok(queryService.getDocumentDetails(id, orgId));
    }

    // UC16.3: Tải file PDF chính thức
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id, Authentication authentication) {
        Long orgId = resolveUserOrgId(authentication);
        byte[] pdfBytes = queryService.downloadDocument(id, orgId);
        ByteArrayResource resource = new ByteArrayResource(pdfBytes);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document_" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfBytes.length)
                .body(resource);
    }
}