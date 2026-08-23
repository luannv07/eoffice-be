package vn.edu.epu.eoffice_be.dto.request;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DictionaryItemFilterRequest extends BaseFilterRequest {

    String groupCd;

    String itemCd;

    String nameVn;

    String nameEn;

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
