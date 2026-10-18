package vn.edu.epu.eoffice_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Response quyết định bổ nhiệm chức vụ")
public class PositionAssignmentResponse {

	@Schema(description = "ID quyết định bổ nhiệm", example = "5001")
	Long id;

	@Schema(description = "ID tài khoản", example = "1001")
	Long userLoginId;

	@Schema(description = "Username tài khoản", example = "nguyenvana")
	String username;

	@Schema(description = "ID đơn vị", example = "2001")
	Long orgNodeId;

	@Schema(description = "Tên đơn vị tiếng Việt", example = "Phòng Hành chính - Tổng hợp")
	String orgNodeNameVn;

	@Schema(description = "Mã chức vụ", example = "TP")
	String positionCd;

	@Schema(description = "Tên chức vụ tiếng Việt", example = "Trưởng phòng")
	String positionName;

	@Schema(description = "Thời điểm có hiệu lực", example = "2026-01-01T00:00:00Z")
	Instant effectiveFrom;

	@Schema(description = "Thời điểm hết hiệu lực", example = "2027-12-31T23:59:59Z")
	Instant effectiveTo;

	@Schema(description = "Ngày miễn nhiệm thực tế (null = đang hiệu lực)")
	Instant actualDismissalDate;

	@Schema(description = "Mã lý do miễn nhiệm")
	String dismissalReasonCd;

	@Schema(description = "Ghi chú miễn nhiệm")
	String dismissalNote;

	@Schema(description = "Đã xóa mềm", example = "false")
	boolean deleteFlg;

	@Schema(description = "Ngày tạo")
	Instant createdDate;

	@Schema(description = "Ngày cập nhật cuối")
	Instant updatedDate;
}
