package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Schema(description = "Request tạo mục từ điển mới")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DictionaryItemCreateRequest {

    @Schema(description = "Mã nhóm từ điển (viết hoa, gạch dưới). Ví dụ nhóm hiện có: USER_STATUS_CD, EMPLOYMENT_STATUS_CD, DISMISSAL_REASON_CD, ORG_NODE_TYPE_CD",
            example = "USER_STATUS_CD", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "groupCd không được để trống")
    @Size(max = 50, message = "groupCd tối đa 50 ký tự")
    String groupCd;

    @Schema(description = "Mã mục (duy nhất trong nhóm, viết hoa, gạch dưới). Với USER_STATUS_CD: ACTIVE / LOCKED / DISABLED",
            example = "SUSPENDED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "itemCd không được để trống")
    @Size(max = 50, message = "itemCd tối đa 50 ký tự")
    String itemCd;

    @Schema(description = "Tên hiển thị tiếng Việt", example = "Tạm ngưng hoạt động", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "nameVn không được để trống")
    @Size(max = 255, message = "nameVn tối đa 255 ký tự")
    String nameVn;

    @Schema(description = "Tên hiển thị tiếng Anh", example = "Suspended")
    @Size(max = 255, message = "nameEn tối đa 255 ký tự")
    String nameEn;

    @Schema(description = "Thứ tự sắp xếp khi hiển thị (mặc định 0)", example = "3")
    Integer displayOrder;

    @Schema(description = "Mục có được dùng hay không (mặc định true)", example = "true")
    Boolean activeFlg;
}
