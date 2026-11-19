// Trả về quyền chức vụ sau khi gán.
package vn.edu.epu.eoffice_be.dto.response;

import lombok.Builder;
import lombok.Getter;

// Mô tả bản ghi quyền chức vụ được lưu.
@Getter
@Builder
public class PositionPermissionResponse {
    private Long id;
    private String positionCd;
    private String permissionCd;
    private boolean inheritToChildren;
}
