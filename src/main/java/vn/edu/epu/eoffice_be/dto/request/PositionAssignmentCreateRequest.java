package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Schema(description = "Request tạo quyết định bổ nhiệm chức vụ cho tài khoản")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PositionAssignmentCreateRequest {

	@Schema(description = "ID tài khoản (user_logins.id)", example = "1001", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull(message = "userLoginId không được để trống")
	Long userLoginId;

	@Schema(description = "ID đơn vị tổ chức (org_nodes.id)", example = "2001", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull(message = "orgNodeId không được để trống")
	Long orgNodeId;

	@Schema(description = "Mã chức vụ: HT / PHT / TK / PK / TP / PP / CV", example = "TP", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "positionCd không được để trống")
	@Size(max = 50, message = "positionCd tối đa 50 ký tự")
	String positionCd;

	@Schema(description = "Thời điểm có hiệu lực (ISO-8601). Null = hiệu lực ngay", example = "2026-01-01T00:00:00Z")
	Instant effectiveFrom;

	@Schema(description = "Thời điểt hết hiệu lực (ISO-8601). Null = không xác định", example = "2027-12-31T23:59:59Z")
	Instant effectiveTo;
}
