package vn.edu.epu.eoffice_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
import vn.edu.epu.eoffice_be.dto.request.DictionaryItemBatchDeleteRequest;
import vn.edu.epu.eoffice_be.dto.request.DictionaryItemCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.DictionaryItemFilterRequest;
import vn.edu.epu.eoffice_be.dto.request.DictionaryItemUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.DictionaryItemResponse;
import vn.edu.epu.eoffice_be.service.DictionaryItemService;

@RestController
@RequestMapping("/dictionary-items")
@RequiredArgsConstructor
@Auditable
@Tag(name = "Dictionary Item",
        description = "Quản lý mục từ điển dữ liệu (dictionary items): tra cứu, tạo, cập nhật, xóa mềm theo nhóm mã dùng chung của hệ thống")
public class DictionaryItemController {

    private final DictionaryItemService dictionaryItemService;

    @Operation(
            summary = "Danh sách mục từ điển (phân trang)",
            description = "Trả về danh sách mục từ điển đang hoạt động (chưa xóa mềm) dạng phân trang. "
                    + "Hỗ trợ filter: groupCd, itemCd, nameVn, nameEn (contains, không phân biệt hoa thường), activeFlg (khớp chính xác). "
                    + "Hỗ trợ sortBy: createdDate, updatedDate, displayOrder, groupCd, itemCd, nameVn (mặc định groupCd ASC)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số phân trang/sắp xếp không hợp lệ")
    })
    @GetMapping
    public BaseResponse<Page<DictionaryItemResponse>> getAll(
            @Valid @ModelAttribute DictionaryItemFilterRequest request) {
        return BaseResponse.success(dictionaryItemService.getAll(request));
    }

    @Operation(summary = "Chi tiết mục từ điển", description = "Lấy thông tin một mục từ điển theo id (đã bị xóa mềm sẽ trả 404).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tìm thấy mục từ điển"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy hoặc đã bị xóa (NOT_FOUND)")
    })
    @GetMapping("/{id}")
    public BaseResponse<DictionaryItemResponse> getDetail(@PathVariable Long id) {
        return BaseResponse.success(dictionaryItemService.getDetail(id));
    }

    @Operation(
            summary = "Tạo mục từ điển mới",
            description = "Tạo mục từ điển trong nhóm groupCd. Cặp (groupCd, itemCd) phải duy nhất. "
                    + "Nếu cặp đã tồn tại nhưng đang ở trạng thái xóa mềm, hệ thống sẽ phục hồi dòng cũ và ghi đè nội dung từ request "
                    + "(giữ nguyên id và thông tin audit ban đầu)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tạo/phục hồi thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ (thiếu trường bắt buộc, vượt độ dài)"),
            @ApiResponse(responseCode = "409", description = "Cặp (groupCd, itemCd) đã tồn tại và đang hoạt động (DICTIONARY_ITEM_ALREADY_EXISTS)")
    })
    @PostMapping
    public BaseResponse<DictionaryItemResponse> create(@Valid @RequestBody DictionaryItemCreateRequest request) {
        return BaseResponse.success(dictionaryItemService.create(request));
    }

    @Operation(
            summary = "Cập nhật mục từ điển",
            description = "Cập nhật nội dung mục từ điển. Không cho phép thay đổi groupCd/itemCd — "
                    + "hai trường này bất khả biến vì được các nghiệp vụ khác tham chiếu theo mã."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy hoặc đã bị xóa (NOT_FOUND)")
    })
    @PutMapping("/{id}")
    public BaseResponse<DictionaryItemResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody DictionaryItemUpdateRequest request) {
        return BaseResponse.success(dictionaryItemService.update(id, request));
    }

    @Operation(
            summary = "Xóa mục từ điển",
            description = "Xóa mềm (delete_flg = true). Mục vẫn còn trong DB để bảo toàn lịch sử tham chiếu."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Xóa thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy hoặc đã bị xóa (NOT_FOUND)")
    })
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable Long id) {
        dictionaryItemService.delete(id);
        return BaseResponse.success(null);
    }

    @Operation(
            summary = "Xóa nhiều mục từ điển",
            description = "Xóa mềm nhiều mục từ điển trong cùng một transaction. Chỉ các id tồn tại và chưa xóa mềm được tính; "
                    + "trả về số lượng bản ghi thực tế đã xóa."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Xóa hàng loạt thành công"),
            @ApiResponse(responseCode = "400", description = "Danh sách ids rỗng hoặc không hợp lệ")
    })
    @PostMapping("/batch-delete")
    public BaseResponse<Long> batchDelete(@Valid @RequestBody DictionaryItemBatchDeleteRequest request) {
        return BaseResponse.success(dictionaryItemService.batchDelete(request));
    }
}
