package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.entity.BaseEntity;

import java.time.Instant;

@Entity
@Table(name = "document_distributions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentDistribution extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "document_id", nullable = false)
    Long documentId;

    @Column(name = "target_type", nullable = false, length = 20)
    String targetType; // ALL, ORG_NODE, USER

    @Column(name = "target_id")
    Long targetId; // org_node_id hoac user_id (null neu ALL)

    @Column(name = "is_read", nullable = false)
    boolean isRead = false;

    @Column(name = "read_at")
    Instant readAt;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean getIsRead() {
        return Boolean.TRUE.equals(this.isRead);
    }

    public void setIsRead(boolean isRead) {
        this.isRead = isRead;
    }
}