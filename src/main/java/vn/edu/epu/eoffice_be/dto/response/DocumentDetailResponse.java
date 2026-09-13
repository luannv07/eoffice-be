package vn.edu.epu.eoffice_be.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentDetailResponse {

    DocumentResponse document;
    List<DocumentAttachmentResponse> attachments;
    List<DocumentVersionResponse> versions;
}
