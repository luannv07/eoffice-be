package vn.edu.epu.eoffice_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryItemResponse {
    Long id;
    String groupCd;
    String itemCd;
    String nameVn;
    String nameEn;
    Integer displayOrder;
    Boolean activeFlg;
    Instant createdDate;
    Instant updatedDate;
}
