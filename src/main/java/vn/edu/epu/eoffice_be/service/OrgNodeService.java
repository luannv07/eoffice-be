package vn.edu.epu.eoffice_be.service;

import vn.edu.epu.eoffice_be.dto.request.OrgNodeCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.OrgNodeFilterRequest;
import vn.edu.epu.eoffice_be.dto.request.OrgNodeUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.OrgNodeResponse;

import java.util.List;

public interface OrgNodeService {
	// hàm lấy danh sách hệ thống phòng/ban theo dạng cây
	List<OrgNodeResponse> getAll(OrgNodeFilterRequest request);
	// hàm update tree (basic info + tái cấu trúc cây)
	List<OrgNodeResponse> update(OrgNodeUpdateRequest request);
	OrgNodeResponse getDetail(Long id);
	void delete(Long id);
	void create(OrgNodeCreateRequest request);
}
