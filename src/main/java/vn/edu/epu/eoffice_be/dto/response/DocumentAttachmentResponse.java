package vn.edu.epu.eoffice_be.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class DocumentAttachmentResponse {

    Long id;
    Long documentId;
    Long documentVersionId;
    String originalFileName;
    String fileExtension;
    String mimeType;
    long fileSizeBytes;
    String sha256Checksum;

    @JsonProperty("isMainDocument")
    boolean isMainDocument;
    Long uploadedByUserId;
    String uploadedByUsername;
    Instant createdDate;
}
