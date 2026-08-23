package vn.edu.epu.eoffice_be.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DictionaryItemBatchDeleteRequest {

    @NotEmpty
    List<Long> ids;
}
