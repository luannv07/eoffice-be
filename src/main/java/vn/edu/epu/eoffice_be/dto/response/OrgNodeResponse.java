package vn.edu.epu.eoffice_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgNodeResponse {
	private Long id;
	private Long parentId;
	private String code;
	private String nameVn;
	private String nameEn;
	private String typeCd;
	private Integer orgOrder;

	@Builder.Default
	private List<OrgNodeResponse> children = new ArrayList<>();
}
