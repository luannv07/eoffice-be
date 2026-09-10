package vn.edu.epu.eoffice_be.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PublishRequest {
    Long documentBookId;
    LocalDate issueDate;
    LocalDate effectiveDate;
    Boolean isPublicToAll;
    List<Long> recipientOrgIds;
}