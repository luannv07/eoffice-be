package vn.edu.epu.eoffice_be.service;

import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.entity.UserLogin;

/** Document business authority is separate from administration and read access. */
public final class DocumentAdminPolicy {
    private DocumentAdminPolicy() { }

    public static boolean isAdmin(UserLogin user) {
        return user != null && (user.getRole() == Role.ADMIN || user.getRole() == Role.SYSTEM_ADMIN);
    }

    public static void assertBusinessActor(UserLogin actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if (isAdmin(actor)) throw new BusinessException(ErrorCode.FORBIDDEN,
                "Quản trị viên chỉ được xem văn bản và ký xác nhận khi gửi dự thảo của chính mình.");
    }
}
