package vn.edu.epu.eoffice_be.security;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.security.Principal;
import java.util.List;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JwtUserPrincipal implements Principal, Serializable, UserIdAwarePrincipal, OrgAwarePrincipal {

	Long userId;

	String username;

	String role;

	boolean profileCompleted;

	List<String> permissions;

	Long orgId;

	public JwtUserPrincipal(Long userId, String username, String role, boolean profileCompleted, List<String> permissions) {
		this(userId, username, role, profileCompleted, permissions, null);
	}

	public JwtUserPrincipal(Long userId, String username, String role, boolean profileCompleted, List<String> permissions, Long orgId) {
		this.userId = userId;
		this.username = username;
		this.role = role;
		this.profileCompleted = profileCompleted;
		this.permissions = permissions;
		this.orgId = orgId;
	}

	@Override
	public Long getOrgId() {
		return orgId;
	}

	public Long orgId() {
		return orgId;
	}

	// Record-style accessors for compatibility with previous record usage
	public Long userId() {
		return userId;
	}

	public String username() {
		return username;
	}

	public String role() {
		return role;
	}

	public boolean profileCompleted() {
		return profileCompleted;
	}

	public List<String> permissions() {
		return permissions;
	}

	@Override
	public String getName() {
		return username;
	}
}