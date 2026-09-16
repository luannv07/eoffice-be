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
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentWorkflowLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "document_workflow_logs_seq")
    @SequenceGenerator(name = "document_workflow_logs_seq", sequenceName = "document_workflow_logs_seq", allocationSize = 50)
    @Setter(AccessLevel.NONE)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    @Setter(AccessLevel.NONE)
    Document document;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Setter(AccessLevel.NONE)
    WorkflowAction action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    @Setter(AccessLevel.NONE)
    UserLogin actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 50)
    @Setter(AccessLevel.NONE)
    DocumentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 50)
    @Setter(AccessLevel.NONE)
    DocumentStatus toStatus;

    @Column(length = 2000)
    String comment;

    @Column(name = "leadership_instruction", length = 2000)
    String leadershipInstruction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_org_node_id")
    OrgNode assignedOrgNode;

    @Column(name = "signature_hash", length = 64)
    @Setter(AccessLevel.NONE)
    String signatureHash;

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
            java.time.Instant instantFromAt = createdAt.atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
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

    void setCreatedAt(LocalDateTime createdAt) {
        setCreatedDate(createdAt != null ? createdAt.atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant() : null);
    }
    
    public Long getDocumentId() {
        return document != null ? document.getId() : null;
    }

    public void setDeleteFlg(boolean deleteFlg) {
        this.deleteFlg = deleteFlg;
    }

    public static class DocumentWorkflowLogBuilder {
        private Long id;
        private Document document;
        private WorkflowAction action;
        private UserLogin actor;
        private DocumentStatus fromStatus;
        private DocumentStatus toStatus;
        private String comment;
        private String leadershipInstruction;
        private OrgNode assignedOrgNode;
        private String signatureHash;
        private Boolean deleteFlg = false;
        private Instant createdDate;
        private LocalDateTime createdAt;

        DocumentWorkflowLogBuilder() {}

        public DocumentWorkflowLogBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public DocumentWorkflowLogBuilder document(Document document) {
            this.document = document;
            return this;
        }

        public DocumentWorkflowLogBuilder documentId(Long documentId) {
            this.document = Document.builder().id(documentId).build();
            return this;
        }

        public DocumentWorkflowLogBuilder action(WorkflowAction action) {
            this.action = action;
            return this;
        }

        public DocumentWorkflowLogBuilder action(String action) {
            if (action == null || action.trim().isEmpty()) {
                throw new IllegalArgumentException("Action string must not be null or blank");
            }
            try {
                this.action = WorkflowAction.valueOf(action.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid workflow action: " + action, e);
            }
            return this;
        }

        public DocumentWorkflowLogBuilder actor(UserLogin actor) {
            this.actor = actor;
            return this;
        }

        public DocumentWorkflowLogBuilder fromStatus(DocumentStatus fromStatus) {
            this.fromStatus = fromStatus;
            return this;
        }

        public DocumentWorkflowLogBuilder toStatus(DocumentStatus toStatus) {
            this.toStatus = toStatus;
            return this;
        }

        public DocumentWorkflowLogBuilder comment(String comment) {
            this.comment = comment;
            return this;
        }

        public DocumentWorkflowLogBuilder leadershipInstruction(String leadershipInstruction) {
            this.leadershipInstruction = leadershipInstruction;
            return this;
        }

        public DocumentWorkflowLogBuilder assignedOrgNode(OrgNode assignedOrgNode) {
            this.assignedOrgNode = assignedOrgNode;
            return this;
        }

        public DocumentWorkflowLogBuilder signatureHash(String signatureHash) {
            this.signatureHash = signatureHash;
            return this;
        }

        public DocumentWorkflowLogBuilder deleteFlg(Boolean deleteFlg) {
            this.deleteFlg = deleteFlg;
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

        public DocumentWorkflowLog build() {
            return new DocumentWorkflowLog(id, document, action, actor, fromStatus, toStatus,
                    comment, leadershipInstruction, assignedOrgNode, signatureHash,
                    deleteFlg, createdDate, createdAt);
        }
    }
}