package vn.edu.epu.eoffice_be.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IntegrityVerifyResponse {
    boolean valid;
    Long documentId;
    String officialNumber;
    String hashStored;
    String hashComputed;
    Instant verifiedAt;
}