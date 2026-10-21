package vn.edu.epu.eoffice_be.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds the {@link SecurityContext} used by {@link WithMockEpuUser}.
 *
 * <p>It mirrors {@code JwtAuthenticationFilter}: a {@link JwtUserPrincipal} plus a
 * single {@code ROLE_<role>} authority (and any extra permissions requested through
 * the annotation).
 */
public class WithMockEpuUserSecurityContextFactory implements WithSecurityContextFactory<WithMockEpuUser> {

	@Override
	public SecurityContext createSecurityContext(WithMockEpuUser annotation) {
		List<String> permissions = Arrays.asList(annotation.permissions());

		JwtUserPrincipal principal = new JwtUserPrincipal(
						annotation.userId(),
						annotation.username(),
						annotation.role(),
						annotation.profileCompleted(),
						permissions,
						annotation.orgId());

		List<GrantedAuthority> authorities = new ArrayList<>();
		authorities.add(new SimpleGrantedAuthority("ROLE_" + normalizeRole(annotation.role())));
		permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));

		Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		return context;
	}

	private static String normalizeRole(String role) {
		if (role == null || role.isBlank()) {
			return "USER";
		}
		return role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role;
	}
}
