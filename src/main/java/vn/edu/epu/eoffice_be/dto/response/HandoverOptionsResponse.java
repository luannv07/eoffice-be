package vn.edu.epu.eoffice_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.epu.eoffice_be.dto.response.SubmissionOptionsResponse.SelectionMode;

import java.util.List;

/**
 * Thông tin lựa chọn lãnh đạo nhận bàn giao duyệt tiếp từ cấp thẩm định.
 */
@Schema(description = "Thông tin lựa chọn lãnh đạo nhận bàn giao duyệt tiếp")
public record HandoverOptionsResponse(
        @Schema(description = "Chế độ chọn: AUTO (tự gán) hoặc SELECT (bắt buộc chọn)")
        SelectionMode selectionMode,
        @Schema(description = "Danh sách lãnh đạo hợp lệ để nhận bàn giao")
        List<EligibleApproverResponse> approvers
) {
}
