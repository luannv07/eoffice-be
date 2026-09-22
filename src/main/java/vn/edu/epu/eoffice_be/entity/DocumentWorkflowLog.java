package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.entity.BaseEntity;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "document_workflow_logs",
        indexes = {
                @Index(name = "idx_doc_wf_document_id", columnList = "document_id"),
                @Index(name = "idx_dwl_actor", columnList = "actor_id")
        }
)
@Getter
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentWorkflowLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "document_workflow_logs_seq")
    @SequenceGenerator(name = "document_workflow_logs_seq", sequenceName = "document_workflow_logs_seq", allocationSize = 50)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    Document document;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    WorkflowAction action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    UserLogin actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 50)
    DocumentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 50)
    DocumentStatus toStatus;

    @Column(length = 2000)
    String comment;

    @Column(name = "leadership_instruction", length = 2000)
    String leadershipInstruction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_org_node_id")
    OrgNode assignedOrgNode;

    @Column(name = "signature_hash", length = 64)
    String signatureHash;

    @Builder.Default
    @Column(name = "delete_flg", nullable = false)
    boolean deleteFlg = false;

    public DocumentWorkflowLog(Long id, Document document, WorkflowAction action, UserLogin actor,
                               DocumentStatus fromStatus, DocumentStatus toStatus, String comment,
                               String leadershipInstruction, OrgNode assignedOrgNode, String signatureHash,
                               boolean deleteFlg) {
        this(id, document, action, actor, fromStatus, toStatus, comment, leadershipInstruction, assignedOrgNode, signatureHash, deleteFlg, null, null);
    }

    public DocumentWorkflowLog(Long id, Document document, WorkflowAction action, UserLogin actor,
                               DocumentStatus fromStatus, DocumentStatus toStatus, String comment,
                               String leadershipInstruction, OrgNode assignedOrgNode, String signatureHash,
                               Boolean deleteFlg, Instant createdDate, LocalDateTime createdAt) {
        this.id = id;
        this.document = document;
        this.action = action;
        this.actor = actor;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.comment = comment;
        this.leadershipInstruction = leadershipInstruction;
        this.assignedOrgNode = assignedOrgNode;
        this.signatureHash = signatureHash;
        this.deleteFlg = deleteFlg != null ? deleteFlg : false;

        if (createdDate != null && createdAt != null) {
            java.time.Instant instantFromAt = createdAt.atZone(Document.getBusinessZone()).toInstant();
            if (!createdDate.equals(instantFromAt)) {
                throw new IllegalArgumentException("createdDate và createdAt không khớp giá trị");
            }
        }

        if (createdDate != null) {
            setCreatedDate(createdDate);
        } else if (createdAt != null) {
            setCreatedAt(createdAt);
        }
    }

    public static DocumentWorkflowLogBuilder builder() {
        return new DocumentWorkflowLogBuilder();
    }

    public LocalDateTime getCreatedAt() {
        return getCreatedDate() != null
                ? LocalDateTime.ofInstant(getCreatedDate(), Document.getBusinessZone())
                : null;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        setCreatedDate(createdAt != null ? createdAt.atZone(Document.getBusinessZone()).toInstant() : null);
    }   
    
    public Long getDocumentId() {
        return document != null ? document.getId() : null;
    }

    // Chỉ mở duy nhất setter xóa mềm
    public void setDeleteFlg(boolean deleteFlg) {
        this.deleteFlg = deleteFlg;
    }
}