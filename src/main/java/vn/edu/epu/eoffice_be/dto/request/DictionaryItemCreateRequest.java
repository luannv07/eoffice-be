package vn.edu.epu.eoffice_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DictionaryItemCreateRequest {

    @NotBlank
    @Size(max = 50)
    String groupCd;

    @NotBlank
    @Size(max = 50)
    String itemCd;

    @NotBlank
    @Size(max = 255)
    String nameVn;

    @Size(max = 255)
    String nameEn;

    Integer displayOrder;

    Boolean activeFlg;
}
