package vn.edu.epu.eoffice_be.utils;

import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;

public final class EnumUtils {

	private EnumUtils() {
	}

	public static <T extends Enum<T>> T fromString(Class<T> enumType, String value) {
		if (value == null || value.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_ENUM_VALUE);
		}
		try {
			return Enum.valueOf(enumType, value.trim());
		} catch (IllegalArgumentException ex) {
			throw new BusinessException(ErrorCode.INVALID_ENUM_VALUE);
		}
	}

	public static <T extends Enum<T>> T fromString(Class<T> enumType, String value, T defaultValue) {
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			return Enum.valueOf(enumType, value.trim());
		} catch (IllegalArgumentException ex) {
			return defaultValue;
		}
	}
}