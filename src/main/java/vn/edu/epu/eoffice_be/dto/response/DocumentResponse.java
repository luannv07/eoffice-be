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
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentResponse {

    Long id;

    private boolean testDocument;
    String draftCode;
    String title;
    String summary;
    String content;
    String documentTypeCd;
    String documentTypeName;
    Long issuingOrgNodeId;
    String issuingOrgNodeName;
    Long drafterId;
    String drafterUsername;
    String drafterFullName;
    DocumentStatus status;
    String statusDescription;
    int currentVersion;
    DocumentSecurityLevel securityLevel;
    DocumentUrgencyLevel urgencyLevel;
    Instant submittedAt;
    Instant createdDate;
    Instant updatedDate;

    // UC15-UC17: Các trường phát hành văn bản
    String officialNumber;
    Long documentBookId;
    String documentBookCode;
    LocalDate issueDate;
    LocalDate effectiveDate;
    String officialFilePath;
    String officialHash;
}
