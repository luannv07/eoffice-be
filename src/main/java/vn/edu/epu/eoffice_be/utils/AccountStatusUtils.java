package vn.edu.epu.eoffice_be.utils;

import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.UserStatusCd;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.entity.UserLogin;

import java.time.Instant;

public final class AccountStatusUtils {

	private AccountStatusUtils() {
	}

	public static void checkLoginAllowed(UserLogin user, Instant now) {
		if (UserStatusCd.LOCKED.name().equals(user.getUserStatusCd())
						|| (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(now))) {
			throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
		}
		if (UserStatusCd.DISABLED.name().equals(user.getUserStatusCd())) {
			throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
		}
	}
}
