package vn.edu.epu.eoffice_be.dto.request;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrgNodeFilterRequest extends BaseFilterRequest {

	String code;

	String nameVn;

	String typeCd;
}