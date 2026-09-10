package vn.edu.epu.eoffice_be.common.enums;

import vn.edu.epu.eoffice_be.utils.EnumUtils;

public enum UserStatusCd {
	ACTIVE,
	LOCKED,
	DISABLED;

	public static UserStatusCd from(String value) {
		return EnumUtils.fromString(UserStatusCd.class, value);
	}
}