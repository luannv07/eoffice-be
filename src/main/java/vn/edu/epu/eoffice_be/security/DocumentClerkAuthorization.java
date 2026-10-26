package vn.edu.epu.eoffice_be.security;

import org.springframework.security.core.Authentication;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.entity.UserLogin;

/** Shared operational policy for numbering, clerk initials and publication. */
public final class DocumentClerkAuthorization {
    private DocumentClerkAuthorization() { }

    public static boolean isAllowed(UserLogin actor, Authentication authentication) {
        if (actor == null) return false;
        if (actor.getRole() == Role.CLERK || actor.getRole() == Role.ADMIN
                || actor.getRole() == Role.SYSTEM_ADMIN) return true;
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(a ->
                "DOCUMENT_ASSIGN_NUMBER".equals(a.getAuthority()));
    }
}
