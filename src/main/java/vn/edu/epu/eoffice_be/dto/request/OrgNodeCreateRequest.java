package vn.edu.epu.eoffice_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrgNodeCreateRequest {

	@NotBlank
	@Size(max = 50)
	String code;

	Long parentId;

	@NotBlank
	@Size(max = 255)
	String nameVn;

	@NotBlank
	@Size(max = 255)
	String nameEn;

	@NotBlank
	@Size(max = 50)
	String typeCd;

	Integer orgOrder;
}