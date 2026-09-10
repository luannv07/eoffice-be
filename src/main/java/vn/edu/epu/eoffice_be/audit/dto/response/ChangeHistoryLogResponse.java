package vn.edu.epu.eoffice_be.audit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Một dòng lịch sử thay đổi dữ liệu: trước/sau khi thay ở mức trường")
public class ChangeHistoryLogResponse {
	@Schema(description = "Id bản ghi log", example = "9a1b2c3d-4e5f-4a6b-8c7d-0f1e2d3c4b5a")
	UUID id;
	@Schema(description = "Tên bảng bị thay đổi", example = "org_nodes")
	String tableName;
	@Schema(description = "Id bản ghi bị thay đổi trong bảng", example = "1015")
	String objectId;
	@Schema(description = "Loại thay đổi: INSERT / UPDATE / DELETE", example = "UPDATE")
	String action;
	@Schema(description = "Người thực hiện thay đổi", example = "nguyen.van.a")
	String changedBy;
	@Schema(description = "Thời điểm thay đổi", example = "2026-08-22T09:05:47Z")
	Instant changedAt;
	@Schema(description = "Service thực hiện thay đổi", example = "eoffice-be")
	String sourceService;
	@Schema(description = "Giá trị trước thay đổi dạng JSON", example = "{\"nameVn\":\"Khoa Công nghệ thông tin\"}")
	String beforeData;
	@Schema(description = "Giá trị sau thay đổi dạng JSON", example = "{\"nameVn\":\"Khoa Công nghệ thông tin và Truyền thông\"}")
	String afterData;
	@Schema(description = "Danh sách trường bị thay đổi", example = "[\"nameVn\"]")
	String changedFields;
}
