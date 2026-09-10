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

import java.time.Instant;

@Entity
@Table(name = "documents")
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

    @Column(nullable = false, length = 500)
    String title;

    @Column(name = "document_type_cd", nullable = false, length = 50)
    String documentTypeCd;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issuing_org_id", nullable = false)
    OrgNode issuingOrg;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drafter_id", nullable = false)
    UserLogin drafter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signer_id")
    UserLogin signer;

    @Column(name = "urgency_level_cd", nullable = false, length = 50)
    @Builder.Default
    String urgencyLevelCd = "NORMAL";

    @Column(name = "security_level_cd", nullable = false, length = 50)
    @Builder.Default
    String securityLevelCd = "NORMAL";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    DocumentStatus status = DocumentStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_document_id")
    Document parentDocument;

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
}