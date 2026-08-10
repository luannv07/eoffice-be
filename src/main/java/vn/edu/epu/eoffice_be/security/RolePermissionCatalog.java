package vn.edu.epu.eoffice_be.security;

import vn.edu.epu.eoffice_be.common.enums.Role;

import java.util.Set;

/**
 * Trả về permission set cụ thể cho từng role.
 * SYSTEM_ADMIN có quyền toàn hệ thống, không bị giới hạn theo org scope.
 */
public final class RolePermissionCatalog {

    public static final String PERM_ORG_READ = "org:read";
    public static final String PERM_ORG_MANAGE = "org:manage";
    public static final String PERM_USER_READ = "user:read";
    public static final String PERM_USER_MANAGE = "user:manage";
    public static final String PERM_AUDIT_READ = "audit:read";
    public static final String PERM_POSITION_READ = "position:read";
    public static final String PERM_POSITION_MANAGE = "position:manage";

    private RolePermissionCatalog() {
    }

    public static boolean hasGlobalPermission(Role role, String permissionCd) {
        if (role == null || permissionCd == null) {
            return false;
        }
        if (role == Role.SYSTEM_ADMIN) {
            return true;
        }
        return switch (role) {
            case ADMIN -> Set.of(
                    PERM_ORG_READ,
                    PERM_ORG_MANAGE,
                    PERM_USER_READ,
                    PERM_USER_MANAGE,
                    PERM_AUDIT_READ,
                    PERM_POSITION_READ,
                    PERM_POSITION_MANAGE
            ).contains(permissionCd);
            default -> false;
        };
    }
}