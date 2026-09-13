package vn.edu.epu.eoffice_be.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WorkflowTimelineResponse {
    Long id;
    Long documentId;
    String actorUsername;
    String actorRole;
    String action;
    String comment; // Bút phê lãnh đạo
    LocalDateTime createdAt;
}