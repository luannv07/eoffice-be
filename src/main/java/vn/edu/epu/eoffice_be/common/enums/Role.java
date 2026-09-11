package vn.edu.epu.eoffice_be.common.enums;

import vn.edu.epu.eoffice_be.utils.EnumUtils;

public enum Role {
	USER,
	ADMIN,
	SYSTEM_ADMIN,
	REVIEWER,
	LEADER;

	public static Role from(String value) {
		return EnumUtils.fromString(Role.class, value);
	}

	public static Role from(String value, Role defaultValue) {
		return EnumUtils.fromString(Role.class, value, defaultValue);
	}
}