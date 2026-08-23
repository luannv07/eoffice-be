package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Bộ lọc danh sách mục từ điển (dạng query param)")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DictionaryItemFilterRequest extends BaseFilterRequest {

    @Schema(description = "Lọc theo mã nhóm từ điển (contains, không phân biệt hoa thường). Ví dụ: USER_STATUS_CD, EMPLOYMENT_STATUS_CD",
            example = "USER_STATUS")
    String groupCd;

    @Schema(description = "Lọc theo mã mục (contains, không phân biệt hoa thường)", example = "LOCK")
    String itemCd;

    @Schema(description = "Lọc theo tên tiếng Việt (contains, không phân biệt hoa thường)", example = "khoá")
    String nameVn;

    @Schema(description = "Lọc theo tên tiếng Anh (contains, không phân biệt hoa thường)", example = "locked")
    String nameEn;

    @Schema(description = "Lọc theo trạng thái kích hoạt. Bỏ trống để lấy cả hai", example = "true")
    Boolean activeFlg;

    @Override
    protected java.util.Set<String> getAllowedSortFields() {
        return java.util.Set.of("createdDate", "updatedDate", "displayOrder", "groupCd", "itemCd", "nameVn");
    }

    @Override
    protected String getDefaultSortBy() {
        return "groupCd";
    }
}
