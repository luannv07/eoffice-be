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
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.entity.BaseEntity;
import vn.edu.epu.eoffice_be.common.enums.ApprovalRequestStatus;

import java.time.Instant;

@Entity
@Table(name = "document_approval_requests")
@Getter
@Setter
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentApprovalRequest extends BaseEntity {

    public DocumentApprovalRequest() {
        this.status = ApprovalRequestStatus.PENDING;
        this.deleteFlg = false;
    }

    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = ApprovalRequestStatus.PENDING;
        }
        if (this.submittedAt == null) {
            this.submittedAt = Instant.now();
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
    @JoinColumn(name = "sender_user_id", nullable = false)
    UserLogin senderUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_user_id", nullable = false)
    UserLogin approverUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    ApprovalRequestStatus status = ApprovalRequestStatus.PENDING;

    @Column(name = "submit_note", columnDefinition = "TEXT")
    String submitNote;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    String decisionNote;

    @Column(name = "submitted_at", nullable = false)
    @Builder.Default
    Instant submittedAt = Instant.now();

    @Column(name = "processed_at")
    Instant processedAt;

    @Column(name = "delete_flg", nullable = false)
    @Builder.Default
    boolean deleteFlg = false;
}
