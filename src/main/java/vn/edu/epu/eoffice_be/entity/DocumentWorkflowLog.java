package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.entity.BaseEntity;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;

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
@AllArgsConstructor
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

    // Helper getter phục vụ tầng DTO/Timeline đọc thời gian theo múi giờ VN
    public LocalDateTime getCreatedAt() {
        return getCreatedDate() != null
                ? LocalDateTime.ofInstant(getCreatedDate(), java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                : null;
    }

    public Long getDocumentId() {
        return document != null ? document.getId() : null;
    }

    // Chỉ mở duy nhất setter xóa mềm
    public void setDeleteFlg(boolean deleteFlg) {
        this.deleteFlg = deleteFlg;
    }
}