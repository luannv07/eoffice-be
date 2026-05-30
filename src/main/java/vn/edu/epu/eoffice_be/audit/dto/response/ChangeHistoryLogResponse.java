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
public class ChangeHistoryLogResponse {
    UUID id;
    String tableName;
    String objectId;
    String action;
    String changedBy;
    Instant changedAt;
    String sourceService;
    String beforeData;
    String afterData;
    String changedFields;
}
