package vn.edu.epu.eoffice_be.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentVersionResponse {

    Long id;
    Long documentId;
    int versionNumber;
    String title;
    String summary;
    String content;
    String changeSummary;
    Long createdByUserId;
    String createdByUsername;
    Instant createdDate;
}
