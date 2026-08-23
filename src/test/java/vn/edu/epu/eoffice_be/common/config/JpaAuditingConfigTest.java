package vn.edu.epu.eoffice_be.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JpaAuditingConfigTest {

    @Test
    void auditorProviderShouldFallbackToSystemWhenNoAuthentication() {
        SecurityContextHolder.clearContext();
        JpaAuditingConfig config = new JpaAuditingConfig();
        AuditorAware<String> auditorAware = config.auditorProvider();
        assertEquals("unknown", auditorAware.getCurrentAuditor().orElse("missing"));
    }

    @Test
    void auditorProviderShouldReturnPrincipalNameWhenAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("tester", "n/a", java.util.List.of())
        );
        JpaAuditingConfig config = new JpaAuditingConfig();
        AuditorAware<String> auditorAware = config.auditorProvider();
        assertEquals("tester", auditorAware.getCurrentAuditor().orElse("missing"));
        SecurityContextHolder.clearContext();
    }

    @Test
    void auditorProviderShouldReturnUsernameWhenPrincipalIsJwtUserPrincipal() {
        JwtUserPrincipal principal = new JwtUserPrincipal(1002L, "admin", "ADMIN", java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, "n/a", java.util.List.of())
        );
        JpaAuditingConfig config = new JpaAuditingConfig();
        AuditorAware<String> auditorAware = config.auditorProvider();
        assertEquals("admin", auditorAware.getCurrentAuditor().orElse("missing"));
        SecurityContextHolder.clearContext();
    }

    @Test
    void auditorProviderShouldFallbackWhenAuthenticationNotAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("tester", "n/a"));
        JpaAuditingConfig config = new JpaAuditingConfig();
        AuditorAware<String> auditorAware = config.auditorProvider();
        assertEquals("unknown", auditorAware.getCurrentAuditor().orElse("missing"));
        SecurityContextHolder.clearContext();
    }

    @Test
    void auditorProviderShouldFallbackWhenPrincipalNameBlank() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("   ", "n/a", java.util.List.of())
        );
        JpaAuditingConfig config = new JpaAuditingConfig();
        AuditorAware<String> auditorAware = config.auditorProvider();
        assertEquals("system", auditorAware.getCurrentAuditor().orElse("missing"));
        SecurityContextHolder.clearContext();
    }
}
