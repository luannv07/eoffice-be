package vn.edu.epu.eoffice_be.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("isPublicToAll")
    Boolean isPublicToAll;
    List<Long> recipientOrgIds;
}