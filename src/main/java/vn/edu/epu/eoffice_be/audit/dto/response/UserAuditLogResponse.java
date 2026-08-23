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
@Schema(description = "Một dòng nhật ký hoạt động người dùng ghi tại controller boundary")
public class UserAuditLogResponse {
    @Schema(description = "Id bản ghi log", example = "7f3c2a1e-5b4d-4e6f-9a8b-1c2d3e4f5a6b")
    UUID id;
    @Schema(description = "Tên đăng nhập của người gọi API", example = "nguyen.van.a")
    String username;
    @Schema(description = "User-Agent của client", example = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/139.0")
    String userAgent;
    @Schema(description = "IP của client", example = "10.10.5.24")
    String clientIp;
    @Schema(description = "URI được gọi", example = "/api/v1/org-nodes")
    String requestUri;
    @Schema(description = "HTTP method", example = "POST")
    String methodName;
    @Schema(description = "Thời gian xử lý (millisecond)", example = "37")
    Long executionTimeMs;
    @Schema(description = "Nội dung tham số request (đã rút gọn/mask)", example = "{\"code\":\"CNTT\",\"nameVn\":\"Khoa Công nghệ thông tin\"}")
    String paramContent;
    @Schema(description = "Thời điểm ghi log", example = "2026-08-22T08:41:12Z")
    Instant createdDate;
}
