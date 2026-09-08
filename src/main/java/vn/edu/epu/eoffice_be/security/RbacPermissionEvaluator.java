package vn.edu.epu.eoffice_be.security;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import vn.edu.epu.eoffice_be.service.PermissionResolver;

import java.io.Serializable;

/**
 * PermissionEvaluator cho @PreAuthorize("hasPermission(<orgNodeId>, '<permissionCd>')").
 * Permission được resolve runtime từ DB theo org scope; SYSTEM_ADMIN được xét toàn cục.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RbacPermissionEvaluator implements PermissionEvaluator {

	final PermissionResolver permissionResolver;

	@Override
	public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
		JwtUserPrincipal principal = principalOf(authentication);
		if (principal == null || targetDomainObject == null || permission == null) {
			return false;
		}
		Long orgNodeId = null;
		if (targetDomainObject instanceof Number number) {
			orgNodeId = number.longValue();
		} else if (targetDomainObject instanceof String str) {
			try {
				orgNodeId = Long.valueOf(str);
			} catch (NumberFormatException ignored) {
				return false;
			}
		}
		return permissionResolver.hasPermission(principal, permission.toString(), orgNodeId);
	}

	@Override
	public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
		JwtUserPrincipal principal = principalOf(authentication);
		if (principal == null) {
			return false;
		}
		Long orgNodeId = null;
		if (targetId instanceof Number number) {
			orgNodeId = number.longValue();
		} else if (targetId instanceof String str) {
			try {
				orgNodeId = Long.valueOf(str);
			} catch (NumberFormatException ignored) {
				orgNodeId = null;
			}
		}
		return permissionResolver.hasPermission(principal, permission.toString(), orgNodeId);
	}

	private JwtUserPrincipal principalOf(Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserPrincipal principal)) {
			return null;
		}
		return principal;
	}
}