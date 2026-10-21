package vn.edu.epu.eoffice_be.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link WithMockEpuUserSecurityContextFactory} builds the same
 * principal shape the production {@code JwtAuthenticationFilter} does.
 *
 * <p>The annotation is read straight from an annotated method (via reflection) so
 * the factory can be exercised without bootstrapping a Spring context.
 */
class WithMockEpuUserSecurityContextFactoryTest {

	private final WithMockEpuUserSecurityContextFactory factory = new WithMockEpuUserSecurityContextFactory();

	@WithMockEpuUser(
					userId = 42L,
					username = "vanthu_epu",
					role = "CLERK",
					orgId = 7L,
					profileCompleted = false,
					permissions = {"DOCUMENT_CREATE"})
	void annotatedSample() {
	}

	@WithMockEpuUser
	void annotatedDefault() {
	}

	@Test
	void createSecurityContextShouldExposeEpuPrincipal() throws Exception {
		SecurityContext context = factory.createSecurityContext(sampleAnnotation());

		Authentication authentication = context.getAuthentication();
		assertNotNull(authentication);
		assertTrue(authentication.isAuthenticated());

		assertInstanceOf(JwtUserPrincipal.class, authentication.getPrincipal());
		JwtUserPrincipal principal = (JwtUserPrincipal) authentication.getPrincipal();
		assertEquals(42L, principal.getUserId());
		assertEquals("vanthu_epu", principal.getUsername());
		assertEquals("CLERK", principal.getRole());
		assertEquals(7L, principal.getOrgId());
		assertFalse(principal.isProfileCompleted());
		assertEquals(List.of("DOCUMENT_CREATE"), principal.getPermissions());

		Set<String> authorities = authentication.getAuthorities().stream()
						.map(GrantedAuthority::getAuthority)
						.collect(Collectors.toSet());
		assertTrue(authorities.contains("ROLE_CLERK"), "role authority must be prefixed with ROLE_");
		assertTrue(authorities.contains("DOCUMENT_CREATE"), "extra permission must be granted");
	}

	@Test
	void createSecurityContextShouldApplySensibleDefaults() throws Exception {
		SecurityContext context = factory.createSecurityContext(defaultAnnotation());

		JwtUserPrincipal principal = (JwtUserPrincipal) context.getAuthentication().getPrincipal();
		assertEquals(1001L, principal.getUserId());
		assertEquals("canbo_test", principal.getUsername());
		assertEquals("USER", principal.getRole());
		assertEquals(1L, principal.getOrgId());
		assertTrue(principal.isProfileCompleted());
		assertTrue(principal.getPermissions().isEmpty());

		Set<String> authorities = context.getAuthentication().getAuthorities().stream()
						.map(GrantedAuthority::getAuthority)
						.collect(Collectors.toSet());
		assertEquals(Set.of("ROLE_USER"), authorities);
	}

	@Test
	void createSecurityContextShouldNotDoublePrefixRole() throws Exception {
		SecurityContext context = factory.createSecurityContext(roleWithPrefixAnnotation());

		Set<String> authorities = context.getAuthentication().getAuthorities().stream()
						.map(GrantedAuthority::getAuthority)
						.collect(Collectors.toSet());
		assertTrue(authorities.contains("ROLE_ADMIN"));
		assertFalse(authorities.contains("ROLE_ROLE_ADMIN"));
	}

	@WithMockEpuUser(role = "ROLE_ADMIN")
	void annotatedRoleWithPrefix() {
	}

	private WithMockEpuUser sampleAnnotation() throws Exception {
		return annotationOf("annotatedSample");
	}

	private WithMockEpuUser defaultAnnotation() throws Exception {
		return annotationOf("annotatedDefault");
	}

	private WithMockEpuUser roleWithPrefixAnnotation() throws Exception {
		return annotationOf("annotatedRoleWithPrefix");
	}

	private WithMockEpuUser annotationOf(String methodName) throws Exception {
		Method method = getClass().getDeclaredMethod(methodName);
		WithMockEpuUser annotation = method.getAnnotation(WithMockEpuUser.class);
		assertNotNull(annotation, "test method " + methodName + " must be annotated with @WithMockEpuUser");
		return annotation;
	}
}
