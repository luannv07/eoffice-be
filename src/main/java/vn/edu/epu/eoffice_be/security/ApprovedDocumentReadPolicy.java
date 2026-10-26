package vn.edu.epu.eoffice_be.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.service.PermissionResolver;

import java.util.Objects;
import java.util.Set;

/**
 * Chính sách phân quyền đọc (metadata và tệp đính kèm) cho văn bản ở trạng thái APPROVED.
 * Văn bản APPROVED là hồ sơ vừa được lãnh đạo phê duyệt nội bộ, chưa cấp số và chưa ban hành chính thức.
 */
@Component
@RequiredArgsConstructor
public class ApprovedDocumentReadPolicy {

    private final PermissionResolver permissionResolver;

    public static final Set<String> ALLOWED_PERMISSIONS = Set.of(
            "DOCUMENT_APPROVE",
            "DOCUMENT_SIGN_OFFICIAL",
            "DOCUMENT_DISTRIBUTE"
    );

    /**
     * Kiểm tra xem người dùng có quyền đọc văn bản ở trạng thái APPROVED hay không.
     *
     * @param document Văn bản cần kiểm tra
     * @param actor    Người dùng cần truy cập (thông tin lấy từ database)
     * @return true nếu thỏa mãn ít nhất một điều kiện hợp lệ, ngược lại false
     */
    public boolean canRead(Document document, UserLogin actor) {
        if (document == null || actor == null) {
            return false;
        }

        // 1. Người tạo văn bản: lấy creatorUser, nếu thiếu thì lấy drafter
        UserLogin creator = document.getCreatorUser() != null ? document.getCreatorUser() : document.getDrafter();
        if (creator != null && Objects.equals(creator.getId(), actor.getId())) {
            return true;
        }

        // 2. Quản trị viên (ADMIN hoặc SYSTEM_ADMIN) từ database
        Role role = actor.getRole();
        if (role == Role.ADMIN || role == Role.SYSTEM_ADMIN) {
            return true;
        }

        // 3. Quyền nghiệp vụ tại đơn vị ban hành (bổ nhiệm, kế thừa hoặc ủy quyền còn hiệu lực)
        Long orgId = document.getOrgId();
        if (orgId == null) {
            return false;
        }

        if (actor.getId() == null) {
            return false;
        }

        for (String perm : ALLOWED_PERMISSIONS) {
            if (permissionResolver.hasPermission(actor.getId(), role, perm, orgId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Xác thực quyền đọc văn bản APPROVED; ném lỗi BusinessException(FORBIDDEN) nếu không đủ quyền.
     *
     * @param document Văn bản cần kiểm tra
     * @param actor    Người dùng cần truy cập
     * @throws BusinessException nếu không đủ thẩm quyền
     */
    public void assertCanRead(Document document, UserLogin actor) {
        if (!canRead(document, actor)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền truy cập văn bản ở trạng thái này");
        }
    }
}
