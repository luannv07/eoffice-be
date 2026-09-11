package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.entity.BaseEntity;
import vn.edu.epu.eoffice_be.common.enums.DocumentProcessAction;

import java.time.Instant;

@Entity
@Table(name = "document_process_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentProcessLog extends BaseEntity {

    @PrePersist
    public void prePersist() {
        if (this.logTime == null) {
            this.logTime = Instant.now();
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eoffice_seq")
    @SequenceGenerator(name = "eoffice_seq", sequenceName = "hibernate_sequence", allocationSize = 50)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", nullable = false)
    UserLogin actorUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    DocumentProcessAction action;

    @Column(name = "from_status", length = 50)
    String fromStatus;

    @Column(name = "to_status", length = 50)
    String toStatus;

    @Column(columnDefinition = "TEXT")
    String note;

    @Builder.Default
    @Column(name = "log_time", nullable = false)
    Instant logTime = Instant.now();

    @Builder.Default
    @Column(name = "delete_flg", nullable = false)
    boolean deleteFlg = false;
}
