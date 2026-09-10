package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_workflow_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentWorkflowLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "document_id", nullable = false)
    Long documentId;

    @Column(name = "actor_username", nullable = false)
    String actorUsername;

    @Column(name = "actor_role")
    String actorRole;

    @Column(name = "action", nullable = false)
    String action;

    @Column(name = "comment", columnDefinition = "TEXT")
    String comment;

    @Column(name = "created_at")
    LocalDateTime createdAt;
}