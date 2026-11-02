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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;
import vn.edu.epu.eoffice_be.common.entity.BaseEntity;
import vn.edu.epu.eoffice_be.common.enums.DocumentSecurityLevel;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.DocumentUrgencyLevel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Entity
@Table(name = "documents")
@SQLDelete(sql = "UPDATE documents SET delete_flg = true WHERE id = ?")
@SQLRestriction("delete_flg = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Document extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eoffice_seq")
    @SequenceGenerator(name = "eoffice_seq", sequenceName = "hibernate_sequence", allocationSize = 50)
    Long id;

    @Column(name = "is_test_document", nullable = false)
    @Builder.Default
    boolean testDocument = false;

    @Column(name = "draft_code", length = 50)
    String draftCode;

    @Column(nullable = false, length = 500)
    String title;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "TEXT")
    String summary;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "TEXT")
    String content;

    @Column(name = "document_type_cd", length = 50)
    String documentTypeCd;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issuing_org_node_id")
    OrgNode issuingOrgNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issuing_org_id")
    OrgNode issuingOrg;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    @JoinColumn(name = "drafter_id", nullable = false)
    UserLogin drafter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signer_id")
    UserLogin signer;

    @Column(name = "current_version", nullable = false)
    @Builder.Default
    int currentVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "security_level", nullable = false, length = 30)
    @Builder.Default
    DocumentSecurityLevel securityLevel = DocumentSecurityLevel.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency_level", nullable = false, length = 30)
    @Builder.Default
    DocumentUrgencyLevel urgencyLevel = DocumentUrgencyLevel.NORMAL;

    @Column(name = "urgency_level_cd", length = 50)
    @Builder.Default
    String urgencyLevelCd = "NORMAL";

    @Column(name = "security_level_cd", length = 50)
    @Builder.Default
    String securityLevelCd = "NORMAL";

    @NotNull(message = "Document status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    DocumentStatus status = DocumentStatus.DRAFT;

    @Column(name = "submitted_at")
    Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_document_id")
    Document parentDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_book_id")
    DocumentBook documentBook;

    @Column(name = "recipients_text", length = 1000)
    String recipientsText;

    @Column(name = "official_number", length = 100)
    String officialNumber;

    @Column(name = "issue_date")
    Instant issueDate;

    @Column(name = "effective_date")
    Instant effectiveDate;

    @Column(name = "official_hash", length = 64)
    String officialHash;

    @Column(name = "primary_file_path", length = 500)
    String primaryFilePath;

    @Column(name = "official_file_path", length = 500)
    String officialFilePath;

    @Column(name = "delete_flg", nullable = false)
    @Builder.Default
    boolean deleteFlg = false;

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public void setStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Document status must not be null or blank");
        }
        try {
            this.status = DocumentStatus.valueOf(status.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown document status: " + status, e);
        }
    }

    @PrePersist
    @PreUpdate
    private void validateStatus() {
        if (this.status == null) {
            throw new IllegalStateException("Document status cannot be null");
        }
        if (this.issuingOrg == null && this.issuingOrgNode != null) {
            this.issuingOrg = this.issuingOrgNode;
        } else if (this.issuingOrgNode == null && this.issuingOrg != null) {
            this.issuingOrgNode = this.issuingOrg;
        }
    }

    public static final ZoneId DEFAULT_BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final java.util.concurrent.atomic.AtomicReference<ZoneId> businessZone =
            new java.util.concurrent.atomic.AtomicReference<>(DEFAULT_BUSINESS_ZONE);

    /**
     * @deprecated Giữ lại cho tương thích ngược với cấu hình và các API cũ.
     */
    @Deprecated
    public static void setBusinessZone(ZoneId zone) {
        businessZone.set(zone != null ? zone : DEFAULT_BUSINESS_ZONE);
    }

    public static ZoneId getBusinessZone() {
        ZoneId zone = businessZone.get();
        return zone != null ? zone : DEFAULT_BUSINESS_ZONE;
    }

    @Deprecated
    public static void resetBusinessZone() {
        businessZone.set(DEFAULT_BUSINESS_ZONE);
    }

    public void setIssueDate(Instant issueDate) {
        this.issueDate = issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        setIssueDate(issueDate, getBusinessZone());
    }

    public void setIssueDate(LocalDate issueDate, ZoneId zoneId) {
        this.issueDate = issueDate != null ? issueDate.atStartOfDay(zoneId != null ? zoneId : getBusinessZone()).toInstant() : null;
    }

    public void setEffectiveDate(Instant effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        setEffectiveDate(effectiveDate, getBusinessZone());
    }

    public void setEffectiveDate(LocalDate effectiveDate, ZoneId zoneId) {
        this.effectiveDate = effectiveDate != null ? effectiveDate.atStartOfDay(zoneId != null ? zoneId : getBusinessZone()).toInstant() : null;
    }

    public Long getOrgId() {
        if (this.issuingOrgNode != null && this.issuingOrgNode.getId() != null) {
            return this.issuingOrgNode.getId();
        }
        if (this.issuingOrg != null && this.issuingOrg.getId() != null) {
            return this.issuingOrg.getId();
        }
        return null;
    }
}
