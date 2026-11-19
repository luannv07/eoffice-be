// Quản lý gán quyền ký nháy cho chức vụ trong database.
package vn.edu.epu.eoffice_be.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.PositionDraftSignRequest;
import vn.edu.epu.eoffice_be.dto.response.PositionPermissionResponse;
import vn.edu.epu.eoffice_be.entity.DictionaryItem;
import vn.edu.epu.eoffice_be.entity.PositionPermission;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DictionaryItemRepository;
import vn.edu.epu.eoffice_be.repository.PositionPermissionRepository;

import java.util.Locale;

// Kiểm tra người quản trị và lưu quyền ký nháy theo chức vụ.
@Service
@RequiredArgsConstructor
public class PositionPermissionService {
    private static final String DRAFT_SIGN_PERMISSION = "DOCUMENT_SIGN_DRAFT";
    private final DocumentReadPolicy users;
    private final DictionaryItemRepository dictionaries;
    private final PositionPermissionRepository permissions;

    // Gán hoặc cập nhật phạm vi quyền ký nháy của chức vụ hợp lệ.
    @Transactional
    public PositionPermissionResponse assignDraftSign(String positionCd, PositionDraftSignRequest request) {
        // Đối chiếu tài khoản và role hiện hành từ database.
        UserLogin actor = users.currentUser();
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền gán quyền ký nháy cho chức vụ.");
        }

        // Kiểm tra dữ liệu cả khi gọi trực tiếp tầng service.
        if (positionCd == null || positionCd.isBlank() || positionCd.length() > 50
                || request == null || request.getInheritToChildren() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Thiếu mã chức vụ hoặc phạm vi kế thừa quyền.");
        }
        String normalizedPositionCd = positionCd.trim().toUpperCase(Locale.ROOT);
        DictionaryItem position = dictionaries.findPositionForPermissionUpdate(normalizedPositionCd)
                .orElseThrow(() -> new BusinessException(ErrorCode.POSITION_CD_INVALID));
        if (!position.isActiveFlg()) {
            throw new BusinessException(ErrorCode.POSITION_CD_INVALID, "Chức vụ không còn hoạt động.");
        }

        // Giữ bản ghi hiện có để gán lại không tạo thêm quyền trùng.
        PositionPermission permission = permissions
                .findByPositionCdAndPermissionCdAndDeleteFlgFalse(normalizedPositionCd, DRAFT_SIGN_PERMISSION)
                .orElseGet(PositionPermission::new);
        permission.setPositionCd(normalizedPositionCd);
        permission.setPermissionCd(DRAFT_SIGN_PERMISSION);
        permission.setInheritToChildren(request.getInheritToChildren());
        permission.setDeleteFlg(false);
        PositionPermission saved = permissions.save(permission);

        // Trả về bản ghi đã lưu cho bên gọi.
        return PositionPermissionResponse.builder()
                .id(saved.getId())
                .positionCd(saved.getPositionCd())
                .permissionCd(saved.getPermissionCd())
                .inheritToChildren(saved.isInheritToChildren())
                .build();
    }
}
