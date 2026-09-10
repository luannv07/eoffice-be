package vn.edu.epu.eoffice_be.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.enums.DocumentSecurityLevel;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.DocumentUrgencyLevel;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentResponse {

    Long id;
    String draftCode;
    String title;
    String summary;
    String content;
    String documentTypeCd;
    String documentTypeName;
    Long issuingOrgNodeId;
    String issuingOrgNodeName;
    Long creatorUserId;
    String creatorUsername;
    String creatorFullName;
    DocumentStatus status;
    String statusDescription;
    int currentVersion;
    DocumentSecurityLevel securityLevel;
    DocumentUrgencyLevel urgencyLevel;
    Instant submittedAt;
    Instant createdDate;
    Instant updatedDate;
}
