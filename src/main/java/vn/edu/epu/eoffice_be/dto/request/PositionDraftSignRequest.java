// Nhận phạm vi kế thừa của quyền ký nháy theo chức vụ.
package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// Mô tả dữ liệu gán quyền ký nháy cho chức vụ.
@Data
@Schema(description = "Gán quyền DOCUMENT_SIGN_DRAFT cho chức vụ")
public class PositionDraftSignRequest {
    @NotNull(message = "inheritToChildren không được để trống")
    @Schema(description = "Cho phép kế thừa quyền xuống đơn vị con", example = "false")
    private Boolean inheritToChildren;
}
