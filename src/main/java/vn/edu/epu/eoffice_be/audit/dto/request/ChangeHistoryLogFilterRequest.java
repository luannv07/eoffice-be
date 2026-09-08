package vn.edu.epu.eoffice_be.audit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import vn.edu.epu.eoffice_be.dto.request.BaseFilterRequest;

import java.time.Instant;
import java.util.Set;

@Getter
@Setter
@Schema(description = "Bộ lọc lịch sử thay đổi dữ liệu (dạng query param)")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangeHistoryLogFilterRequest extends BaseFilterRequest {

	@Schema(description = "Lọc theo tên bảng bị thay đổi (khớp chính xác)", example = "org_nodes")
	@Size(max = 100, message = "tableName tối đa 100 ký tự")
	String tableName;

	@Schema(description = "Lọc theo id bản ghi bị thay đổi", example = "1015")
	@Size(max = 100, message = "objectId tối đa 100 ký tự")
	String objectId;

	@Schema(description = "Lọc theo người thực hiện thay đổi (contains)", example = "admin")
	@Size(max = 100, message = "changedBy tối đa 100 ký tự")
	String changedBy;

	@Schema(description = "Loại thay đổi. Giá trị: INSERT / UPDATE / DELETE", example = "UPDATE")
	@Size(max = 20, message = "action tối đa 20 ký tự")
	String action;

	@Schema(description = "Lọc từ thời điểm thay đổi (ISO 8601, bao gồm)", example = "2026-08-01T00:00:00Z")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	Instant changedFrom;

	@Schema(description = "Lọc đến thời điểm thay đổi (ISO 8601, bao gồm)", example = "2026-08-23T23:59:59Z")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	Instant changedTo;

	public ChangeHistoryLogFilterRequest() {
		setSortBy("changedAt");
		setSortDirection(Sort.Direction.DESC);
	}

	@Override
	protected Set<String> getAllowedSortFields() {
		return Set.of("changedAt");
	}

	@Override
	protected String getDefaultSortBy() {
		return "changedAt";
	}
}
