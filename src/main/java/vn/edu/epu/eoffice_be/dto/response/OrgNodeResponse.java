package vn.edu.epu.eoffice_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "Node tổ chức dạng cây (children lồng nhau)")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgNodeResponse {
	@Schema(description = "Id của node", example = "1015")
	private Long id;

	@Schema(description = "Id node cha (null nếu là node gốc)", example = "1001", nullable = true)
	private Long parentId;

	@Schema(description = "Mã đơn vị (duy nhất)", example = "CNTT")
	private String code;

	@Schema(description = "Tên tiếng Việt", example = "Khoa Công nghệ thông tin")
	private String nameVn;

	@Schema(description = "Tên tiếng Anh", example = "Faculty of Information Technology")
	private String nameEn;

	@Schema(description = "Loại đơn vị (dictionary ORG_NODE_TYPE_CD)", example = "FACULTY")
	private String typeCd;

	@Schema(description = "Thứ tự sắp xếp trong các node cùng cấp", example = "2")
	private Integer orgOrder;

	@Schema(description = "Danh sách node con")
	@Builder.Default
	private List<OrgNodeResponse> children = new ArrayList<>();
}
