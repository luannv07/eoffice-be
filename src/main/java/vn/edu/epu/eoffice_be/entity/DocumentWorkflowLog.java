package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_workflow_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentWorkflowLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "document_id", nullable = false)
    Long documentId;

    @Column(name = "action", length = 100)
    String action;

    @Column(name = "actor", length = 100)
    String actor;

    @Column(name = "actor_username", length = 100)
    String actorUsername;

    @Column(name = "actor_role", length = 100)
    String actorRole;

    @Column(name = "comment", columnDefinition = "TEXT")
    String comment;

    @Column(name = "ip_address", length = 100)
    String ipAddress;

    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}