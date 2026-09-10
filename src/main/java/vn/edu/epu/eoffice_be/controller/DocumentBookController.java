package vn.edu.epu.eoffice_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.dto.request.AssignDocumentNumberRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentBookCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentBookFilterRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentBookUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.AssignDocumentNumberResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentBookResponse;
import vn.edu.epu.eoffice_be.service.DocumentBookService;

@RestController
@RequestMapping("/document-books")
@RequiredArgsConstructor
@Auditable
@Tag(name = "Document Book", description = "Quản lý sổ văn bản 2 cấp và cấp số văn bản (UC06 - UC07)")
public class DocumentBookController {

	private final DocumentBookService documentBookService;

	@Operation(
			summary = "Danh sách sổ văn bản (phân trang & lọc)",
			description = "Lấy danh sách sổ văn bản có phân trang và lọc theo năm, cấp sổ (1: Trường, 2: Phòng/Ban), "
					+ "đơn vị sở hữu, loại văn bản, trạng thái đóng/mở và từ khóa tìm kiếm (mã/tên sổ)."
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
			@ApiResponse(responseCode = "400", description = "Tham số phân trang/sắp xếp không hợp lệ")
	})
	@GetMapping
	public BaseResponse<Page<DocumentBookResponse>> getAll(
			@Valid @ModelAttribute DocumentBookFilterRequest request) {
		return BaseResponse.success(documentBookService.getAll(request));
	}

	@Operation(
			summary = "Chi tiết sổ văn bản",
			description = "Lấy thông tin chi tiết một sổ văn bản theo ID (số hiện tại, mẫu số, loại văn bản, đơn vị sở hữu...)"
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Tìm thấy sổ văn bản"),
			@ApiResponse(responseCode = "404", description = "Không tìm thấy sổ văn bản hoặc đã bị xóa")
	})
	@GetMapping("/{id}")
	public BaseResponse<DocumentBookResponse> getDetail(@PathVariable Long id) {
		return BaseResponse.success(documentBookService.getDetail(id));
	}

	@Operation(
			summary = "Tạo mới sổ văn bản (UC06)",
			description = "Tạo sổ văn bản mới cho năm, đơn vị và loại văn bản. Bắt buộc định dạng mẫu số numberPattern "
					+ "chứa {NUM}/[MÃ_LOẠI]-[MÃ_ĐƠN_VỊ] (ví dụ: {NUM}/QĐ-ĐHEPU hoặc {NUM}/TB-CNTT)."
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Tạo sổ văn bản thành công"),
			@ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ hoặc mẫu số sai định dạng"),
			@ApiResponse(responseCode = "409", description = "Mã sổ hoặc sổ cho năm/đơn vị/loại này đã tồn tại")
	})
	@PostMapping
	public BaseResponse<DocumentBookResponse> create(
			@Valid @RequestBody DocumentBookCreateRequest request) {
		return BaseResponse.success(documentBookService.create(request));
	}

	@Operation(
			summary = "Cập nhật sổ văn bản",
			description = "Cập nhật tên sổ hoặc mẫu số văn bản. Không cho phép sửa năm, đơn vị và loại văn bản sau khi tạo."
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
			@ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ hoặc sổ đã đóng"),
			@ApiResponse(responseCode = "404", description = "Không tìm thấy sổ văn bản")
	})
	@PutMapping("/{id}")
	public BaseResponse<DocumentBookResponse> update(
			@PathVariable Long id,
			@Valid @RequestBody DocumentBookUpdateRequest request) {
		return BaseResponse.success(documentBookService.update(id, request));
	}

	@Operation(
			summary = "Đóng sổ văn bản thủ công",
			description = "Khóa sổ văn bản (is_closed = true), không cho phép tiếp tục cấp số hoặc sửa đổi."
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Đóng sổ thành công"),
			@ApiResponse(responseCode = "404", description = "Không tìm thấy sổ văn bản")
	})
	@PutMapping("/{id}/close")
	public BaseResponse<Void> close(@PathVariable Long id) {
		documentBookService.close(id);
		return BaseResponse.success(null);
	}

	@Operation(
			summary = "Xóa mềm sổ văn bản",
			description = "Đánh dấu xóa mềm sổ văn bản (delete_flg = true)."
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Xóa thành công"),
			@ApiResponse(responseCode = "404", description = "Không tìm thấy sổ văn bản")
	})
	@DeleteMapping("/{id}")
	public BaseResponse<Void> delete(@PathVariable Long id) {
		documentBookService.delete(id);
		return BaseResponse.success(null);
	}

	@Operation(
			summary = "Cấp số văn bản tự động (UC07)",
			description = "Cấp số văn bản tự động tăng dần với Pessimistic Lock, đảm bảo an toàn đa luồng. "
					+ "Trả về số hiệu văn bản theo pattern đã thiết lập (ví dụ: 1/QĐ-ĐHEPU)."
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Cấp số văn bản thành công"),
			@ApiResponse(responseCode = "400", description = "Sổ văn bản đã đóng hoặc thông tin không hợp lệ"),
			@ApiResponse(responseCode = "404", description = "Không tìm thấy sổ văn bản")
	})
	@PostMapping("/assign-number")
	public BaseResponse<AssignDocumentNumberResponse> assignNumber(
			@Valid @RequestBody AssignDocumentNumberRequest request) {
		return BaseResponse.success(documentBookService.assignNumber(request));
	}
}
