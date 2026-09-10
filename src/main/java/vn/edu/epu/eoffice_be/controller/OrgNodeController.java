package vn.edu.epu.eoffice_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.dto.request.OrgNodeCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.OrgNodeFilterRequest;
import vn.edu.epu.eoffice_be.dto.request.OrgNodeUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.OrgNodeResponse;
import vn.edu.epu.eoffice_be.service.OrgNodeService;

import java.util.List;

@RestController
@RequestMapping("/org-nodes")
@RequiredArgsConstructor
@Auditable
@Tag(name = "Org Node", description = "Quản lý cây tổ chức đơn vị: tra cứu, tạo, cập nhật (đổi cha/kéo thả), xóa node")
public class OrgNodeController {

	private final OrgNodeService orgNodeService;

	@Operation(
					summary = "Danh sách cây tổ chức",
					description = "Trả về toàn bộ cây tổ chức dạng lồng nhau (children) từ danh sách node gốc. "
									+ "Hỗ trợ filter theo code, nameVn (contains, không phân biệt hoa thường), typeCd (khớp chính xác). "
									+ "Lưu ý: page/size trong filter không áp dụng cho endpoint này."
	)
	@GetMapping
	public BaseResponse<List<OrgNodeResponse>> getAll(@Valid @ModelAttribute OrgNodeFilterRequest request) {
		return BaseResponse.success(orgNodeService.getAll(request));
	}

	@Operation(summary = "Danh sách đơn vị gốc", description = "Trả về các node không có cha (parent_id = null), sắp xếp theo orgOrder. Dùng làm tầng gốc cho tree lazy-load.")
	@ApiResponses({
					@ApiResponse(responseCode = "200", description = "Danh sách đơn vị gốc")
	})
	@GetMapping("/roots")
	public BaseResponse<List<OrgNodeResponse>> getRoots() {
		return BaseResponse.success(orgNodeService.getRoots());
	}

	@Operation(summary = "Chi tiết đơn vị", description = "Lấy thông tin một node tổ chức theo id (không bao gồm danh sách children).")
	@ApiResponses({
					@ApiResponse(responseCode = "200", description = "Tìm thấy đơn vị"),
					@ApiResponse(responseCode = "404", description = "Không tìm thấy hoặc đã bị xóa (NOT_FOUND)")
	})
	@GetMapping("/{id}")
	public BaseResponse<OrgNodeResponse> getDetail(@PathVariable Long id) {
		return BaseResponse.success(orgNodeService.getDetail(id));
	}

	@Operation(summary = "Danh sách đơn vị con trực tiếp", description = "Trả về các node con cấp 1 của node cha cho trước, sắp xếp theo orgOrder. Dùng cho tree lazy-load theo từng node.")
	@ApiResponses({
					@ApiResponse(responseCode = "200", description = "Danh sách con trực tiếp")
	})
	@GetMapping("/{id}/children")
	public BaseResponse<List<OrgNodeResponse>> getChildren(@PathVariable Long id) {
		return BaseResponse.success(orgNodeService.getChildren(id));
	}

	@Operation(
					summary = "Tạo đơn vị mới",
					description = "Tạo node tổ chức. code phải duy nhất; typeCd phải tồn tại trong dictionary ORG_NODE_TYPE_CD; "
									+ "parentId trỏ tới node đang hoạt động (null = node gốc)."
	)
	@ApiResponses({
					@ApiResponse(responseCode = "200", description = "Tạo thành công"),
					@ApiResponse(responseCode = "400", description = "typeCd không hợp lệ hoặc cha không tồn tại (ORG_NODE_TYPE_INVALID / ORG_NODE_PARENT_NOT_FOUND)"),
					@ApiResponse(responseCode = "409", description = "Mã đơn vị đã tồn tại (ORG_NODE_CODE_EXISTS)")
	})
	@PostMapping
	public BaseResponse<Void> create(
					@Valid @org.springframework.web.bind.annotation.RequestBody
					@io.swagger.v3.oas.annotations.parameters.RequestBody(
									description = "Thông tin đơn vị mới (typeCd nhận UNIVERSITY/FACULTY/DEPARTMENT/CENTER/MAJOR)",
									content = @Content(mediaType = "application/json",
													schema = @Schema(implementation = OrgNodeCreateRequest.class),
													examples = @ExampleObject(value = """
																	{
																	  "code": "CNTT",
																	  "parentId": 1001,
																	  "nameVn": "Khoa Công nghệ thông tin",
																	  "nameEn": "Faculty of Information Technology",
																	  "typeCd": "FACULTY",
																	  "orgOrder": 2
																	}""")))
					OrgNodeCreateRequest request) {
		orgNodeService.create(request);
		return BaseResponse.success(null);
	}

	@Operation(
					summary = "Cập nhật đơn vị",
					description = "Đổi tên và/hoặc di chuyển node sang cha khác (parentId = null → chuyển thành node gốc). "
									+ "Chặn cycle: không cho gán cha là chính nó hoặc node con cháu của nó. Trả về cây tổ chức sau cập nhật."
	)
	@ApiResponses({
					@ApiResponse(responseCode = "200", description = "Cập nhật thành công, trả về cây tổ chức mới"),
					@ApiResponse(responseCode = "400", description = "Cha mới không tồn tại hoặc tạo cycle (ORG_NODE_PARENT_NOT_FOUND / ORG_NODE_CYCLE)"),
					@ApiResponse(responseCode = "404", description = "Không tìm thấy đơn vị (NOT_FOUND)")
	})
	@PutMapping("/{id}")
	public BaseResponse<List<OrgNodeResponse>> update(
					@PathVariable Long id,
					@Valid @org.springframework.web.bind.annotation.RequestBody
					@io.swagger.v3.oas.annotations.parameters.RequestBody(
									description = "Thông tin cập nhật (parentId = null để chuyển thành node gốc)",
									content = @Content(mediaType = "application/json",
													schema = @Schema(implementation = OrgNodeUpdateRequest.class),
													examples = @ExampleObject(value = """
																	{
																	  "id": 1015,
																	  "parentId": 1001,
																	  "nameVn": "Khoa Công nghệ thông tin và Truyền thông",
																	  "nameEn": "Faculty of Information and Communication Technology"
																	}""")))
					OrgNodeUpdateRequest request) {
		request.setId(id);
		return BaseResponse.success(orgNodeService.update(request));
	}

	@Operation(
					summary = "Xóa đơn vị",
					description = "Xóa mềm (delete_flg = true). Chỉ xóa được node lá — node còn đơn vị con sẽ bị từ chối."
	)
	@ApiResponses({
					@ApiResponse(responseCode = "200", description = "Xóa thành công"),
					@ApiResponse(responseCode = "400", description = "Đơn vị còn con (ORG_NODE_HAS_CHILDREN)"),
					@ApiResponse(responseCode = "404", description = "Không tìm thấy đơn vị (NOT_FOUND)")
	})
	@DeleteMapping("/{id}")
	public BaseResponse<Void> delete(@PathVariable Long id) {
		orgNodeService.delete(id);
		return BaseResponse.success(null);
	}
}