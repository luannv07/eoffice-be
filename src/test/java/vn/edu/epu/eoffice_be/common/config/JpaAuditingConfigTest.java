package vn.edu.epu.eoffice_be.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JpaAuditingConfigTest {

    @Test
    void auditorProviderShouldFallbackToSystemWhenNoAuthentication() {
        SecurityContextHolder.clearContext();
        JpaAuditingConfig config = new JpaAuditingConfig();
        AuditorAware<String> auditorAware = config.auditorProvider();
        assertEquals("system", auditorAware.getCurrentAuditor().orElse("missing"));
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
    void auditorProviderShouldFallbackWhenAuthenticationNotAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("tester", "n/a"));
        JpaAuditingConfig config = new JpaAuditingConfig();
        AuditorAware<String> auditorAware = config.auditorProvider();
        assertEquals("system", auditorAware.getCurrentAuditor().orElse("missing"));
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
