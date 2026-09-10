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
import vn.edu.epu.eoffice_be.common.enums.DocumentSecurityLevel;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.DocumentUrgencyLevel;

import java.time.Instant;

/**
 * Quản lý văn bản và dự thảo văn bản với cơ chế xóa mềm (soft-delete).
 * Sử dụng cơ chế @SoftDelete của Hibernate 6.4+/7+ giúp tương thích đa hệ quản trị cơ sở dữ liệu
 * mà không cần hardcode literal true/false trong native SQL.
 * Giới hạn: Điều kiện lọc áp dụng mặc định cho các truy vấn JPA. Khi cần truy vấn văn bản đã xóa
 * để kiểm toán hoặc phục hồi, sử dụng truy vấn Native SQL hoặc Audit Log.
 */
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
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "documents_seq_gen")
    @SequenceGenerator(name = "documents_seq_gen", sequenceName = "documents_seq", allocationSize = 50)
    Long id;

    @Column(name = "draft_code", nullable = false, unique = true, length = 50)
    String draftCode;

    @Column(nullable = false, length = 500)
    String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    String summary;

    @Column(columnDefinition = "TEXT")
    String content;

    @Column(name = "document_type_cd", nullable = false, length = 50)
    String documentTypeCd;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issuing_org_node_id", nullable = false)
    OrgNode issuingOrgNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_user_id", nullable = false)
    UserLogin creatorUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    DocumentStatus status = DocumentStatus.DRAFT;

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

    @Column(name = "submitted_at")
    Instant submittedAt;

    @org.hibernate.annotations.SoftDelete
    @Column(name = "delete_flg", nullable = false)
    @Builder.Default
    boolean deleteFlg = false;
}
