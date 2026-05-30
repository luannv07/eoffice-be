package vn.edu.epu.eoffice_be.audit.dto.response;

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
public class UserAuditLogResponse {
    UUID id;
    String username;
    String userAgent;
    String clientIp;
    String requestUri;
    String methodName;
    Long executionTimeMs;
    String paramContent;
    Instant createdDate;
}
