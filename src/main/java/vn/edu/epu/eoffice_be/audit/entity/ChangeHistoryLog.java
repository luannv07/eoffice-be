package vn.edu.epu.eoffice_be.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "change_history_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangeHistoryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    UUID id;

    @Column(name = "table_name", nullable = false, length = 100)
    String tableName;

    @Column(name = "object_id", nullable = false, length = 100)
    String objectId;

    @Column(name = "action", nullable = false, length = 20)
    String action;

    @Column(name = "changed_by", nullable = false, length = 100)
    String changedBy;

    @Column(name = "changed_at", nullable = false)
    Instant changedAt;

    @Column(name = "source_service", nullable = false, length = 100)
    String sourceService;

    @Column(name = "before_data", columnDefinition = "jsonb")
    String beforeData;

    @Column(name = "after_data", columnDefinition = "jsonb")
    String afterData;

    @Column(name = "changed_fields", columnDefinition = "jsonb")
    String changedFields;
}
