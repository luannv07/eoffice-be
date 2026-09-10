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
@Schema(description = "Bộ lọc nhật ký hoạt động người dùng (dạng query param)")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserAuditLogFilterRequest extends BaseFilterRequest {

	@Schema(description = "Lọc theo tên đăng nhập (contains, không phân biệt hoa thường)", example = "admin")
	@Size(max = 255, message = "username tối đa 255 ký tự")
	String username;

	@Schema(description = "Lọc theo URI đã gọi (contains). Ví dụ: /api/v1/org-nodes, /api/v1/dictionary-items",
					example = "/api/v1/org-nodes")
	@Size(max = 500, message = "requestUri tối đa 500 ký tự")
	String requestUri;

	@Schema(description = "Lọc từ thời điểm (ISO 8601, bao gồm)", example = "2026-08-01T00:00:00Z")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	Instant createdFrom;

	@Schema(description = "Lọc đến thời điểm (ISO 8601, bao gồm)", example = "2026-08-23T23:59:59Z")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	Instant createdTo;

	public UserAuditLogFilterRequest() {
		setSortBy("createdDate");
		setSortDirection(Sort.Direction.DESC);
	}

	@Override
	protected Set<String> getAllowedSortFields() {
		return Set.of("createdDate");
	}

	@Override
	protected String getDefaultSortBy() {
		return "createdDate";
	}
}
