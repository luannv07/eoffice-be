package vn.edu.epu.eoffice_be.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PendingDocumentResponse {
    Long id;
    String title;
    String status;
    String currentHolder;
    Instant updatedAt;
    Long pendingDays;     // Số ngày tồn đọng
    Boolean isOverdue;    // Cảnh báo vượt SLA (> 3 ngày)
}