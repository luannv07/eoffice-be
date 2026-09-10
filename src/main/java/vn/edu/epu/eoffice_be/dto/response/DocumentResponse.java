package vn.edu.epu.eoffice_be.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentResponse {
    Long id;
    String title;
    String officialNumber;
    String status;
    LocalDate issueDate;
    LocalDate effectiveDate;
    String officialHash;
    Boolean isRead;
}