package vn.edu.epu.eoffice_be.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
public class OrgNodeController {

	private final OrgNodeService orgNodeService;

	@GetMapping
	public BaseResponse<List<OrgNodeResponse>> getAll(@Valid @ModelAttribute OrgNodeFilterRequest request) {
		return BaseResponse.success(orgNodeService.getAll(request));
	}

	@GetMapping("/{id}")
	public BaseResponse<OrgNodeResponse> getDetail(@PathVariable Long id) {
		return BaseResponse.success(orgNodeService.getDetail(id));
	}

	@PostMapping
	public BaseResponse<Void> create(@Valid @RequestBody OrgNodeCreateRequest request) {
		orgNodeService.create(request);
		return BaseResponse.success(null);
	}

	@PutMapping("/{id}")
	public BaseResponse<List<OrgNodeResponse>> update(
			@PathVariable Long id,
			@Valid @RequestBody OrgNodeUpdateRequest request) {
		request.setId(id);
		return BaseResponse.success(orgNodeService.update(request));
	}

	@DeleteMapping("/{id}")
	public BaseResponse<Void> delete(@PathVariable Long id) {
		orgNodeService.delete(id);
		return BaseResponse.success(null);
	}
}