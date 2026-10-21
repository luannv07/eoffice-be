package vn.edu.epu.eoffice_be.security;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Populates Spring's {@code SecurityContextHolder} with an EPU E-Office
 * {@link JwtUserPrincipal} so that security-aware tests can run without a real
 * JWT or a running {@code JwtAuthenticationFilter}.
 *
 * <p>This annotation only has an effect in tests backed by the Spring TestContext
 * framework (e.g. {@code @SpringBootTest}, {@code @WebMvcTest}). It does nothing in
 * plain Mockito tests that do not bootstrap a Spring context.
 *
 * <pre>{@code
 * @SpringBootTest
 * @WithMockEpuUser(userId = 1001L, role = "CLERK", orgId = 10L)
 * class DocumentWorkflowSecurityTest { ... }
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockEpuUserSecurityContextFactory.class)
public @interface WithMockEpuUser {

	/** EPU user id (JWT subject). */
	long userId() default 1001L;

	/** Login name of the mock user. */
	String username() default "canbo_test";

	/** Role name as stored in {@code Role} enum, e.g. {@code USER}, {@code CLERK}, {@code LEADER}. */
	String role() default "USER";

	/** Organisation node id the user belongs to. */
	long orgId() default 1L;

	/** Whether the user finished the mandatory profile (drives {@code ProfileCompletedFilter}). */
	boolean profileCompleted() default true;

	/** Extra fine-grained permissions granted to the principal, e.g. {@code DOCUMENT_CREATE}. */
	String[] permissions() default {};
}
