package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Setter
@NoArgsConstructor
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

    @Column(name = "delete_flg", nullable = false)
    boolean deleteFlg = false;

    public DocumentWorkflowLog(Long id, Document document, WorkflowAction action, UserLogin actor,
                               DocumentStatus fromStatus, DocumentStatus toStatus, String comment,
                               String leadershipInstruction, OrgNode assignedOrgNode, String signatureHash,
                               boolean deleteFlg) {
        this(id, document, action, actor, fromStatus, toStatus, comment, leadershipInstruction, assignedOrgNode, signatureHash, deleteFlg, null, null);
    }

    @Builder
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
        if (createdDate != null) {
            setCreatedDate(createdDate);
        } else if (createdAt != null) {
            setCreatedAt(createdAt);
        }
    }

    public LocalDateTime getCreatedAt() {
        return getCreatedDate() != null
                ? LocalDateTime.ofInstant(getCreatedDate(), Document.getBusinessZone())
                : null;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        setCreatedDate(createdAt != null
                ? createdAt.atZone(Document.getBusinessZone()).toInstant()
                : null);
    }

    public Long getDocumentId() {
        return document != null ? document.getId() : null;
    }

    public static class DocumentWorkflowLogBuilder {
        public DocumentWorkflowLogBuilder documentId(Long documentId) {
            this.document = Document.builder().id(documentId).build();
            return this;
        }

        public DocumentWorkflowLogBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            if (createdAt != null) {
                this.createdDate = createdAt.atZone(Document.getBusinessZone()).toInstant();
            } else {
                this.createdDate = null;
            }
            return this;
        }

        public DocumentWorkflowLogBuilder createdDate(Instant createdDate) {
            this.createdDate = createdDate;
            if (createdDate != null) {
                this.createdAt = LocalDateTime.ofInstant(createdDate, Document.getBusinessZone());
            } else {
                this.createdAt = null;
            }
            return this;
        }

        public DocumentWorkflowLogBuilder action(WorkflowAction action) {
            this.action = action;
            return this;
        }

        public DocumentWorkflowLogBuilder action(String action) {
            try {
                this.action = vn.edu.epu.eoffice_be.common.enums.WorkflowAction.valueOf(action);
            } catch (Exception e) {
                this.action = vn.edu.epu.eoffice_be.common.enums.WorkflowAction.SUBMIT;
            }
            return this;
        }
    }
}