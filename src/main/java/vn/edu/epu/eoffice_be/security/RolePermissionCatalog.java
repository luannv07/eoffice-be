package vn.edu.epu.eoffice_be.security;

import vn.edu.epu.eoffice_be.common.enums.Role;

import java.util.Set;

/**
 * Trả về permission set cụ thể cho từng role.
 * Phân định rõ ràng trách nhiệm:
 * - SYSTEM_ADMIN: Quản trị kỹ thuật, cấu hình hệ thống, quản lý từ điển danh mục.
 * - ADMIN: Quản trị người dùng, cấu trúc đơn vị, bổ nhiệm chức vụ và xem nhật ký audit.
 * Cả hai role đều không có quyền nghiệp vụ xử lý văn bản (workflow).
 */
public final class RolePermissionCatalog {

	// Quyền của ADMIN (Quản trị vận hành)
	public static final String PERM_ORG_READ = "org:read";
	public static final String PERM_ORG_MANAGE = "org:manage";
	public static final String PERM_USER_READ = "user:read";
	public static final String PERM_USER_MANAGE = "user:manage";
	public static final String PERM_AUDIT_READ = "audit:read";
	public static final String PERM_POSITION_READ = "position:read";
	public static final String PERM_POSITION_MANAGE = "position:manage";

	// Các mã chuẩn từ role_permissions seed
	public static final String PERM_DB_USER_MANAGE = "USER_MANAGE";
	public static final String PERM_DB_POSITION_ASSIGN = "POSITION_ASSIGN";
	public static final String PERM_DB_ORG_NODE_MANAGE = "ORG_NODE_MANAGE";
	public static final String PERM_DB_AUDIT_LOG_VIEW = "AUDIT_LOG_VIEW";

	// Quyền của SYSTEM_ADMIN (Quản trị hệ thống)
	public static final String PERM_SYSTEM_CONFIG_MANAGE = "SYSTEM_CONFIG_MANAGE";
	public static final String PERM_DICTIONARY_MANAGE = "DICTIONARY_MANAGE";

	private RolePermissionCatalog() {
	}

	public static boolean hasGlobalPermission(Role role, String permissionCd) {
		if (role == null || permissionCd == null) {
			return false;
		}
		return switch (role) {
			case SYSTEM_ADMIN -> Set.of(
					PERM_SYSTEM_CONFIG_MANAGE,
					PERM_DICTIONARY_MANAGE
			).contains(permissionCd);

			case ADMIN -> Set.of(
					PERM_ORG_READ,
					PERM_ORG_MANAGE,
					PERM_USER_READ,
					PERM_USER_MANAGE,
					PERM_AUDIT_READ,
					PERM_POSITION_READ,
					PERM_POSITION_MANAGE,
					PERM_DB_USER_MANAGE,
					PERM_DB_POSITION_ASSIGN,
					PERM_DB_ORG_NODE_MANAGE,
					PERM_DB_AUDIT_LOG_VIEW
			).contains(permissionCd);

			default -> false;
		};
	}
}