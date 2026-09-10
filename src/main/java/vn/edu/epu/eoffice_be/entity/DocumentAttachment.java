package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "document_attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentAttachment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eoffice_seq")
    @SequenceGenerator(name = "eoffice_seq", sequenceName = "hibernate_sequence", allocationSize = 50)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_version_id")
    DocumentVersion documentVersion;

    @Column(name = "original_file_name", nullable = false)
    String originalFileName;

    @Column(name = "storage_file_name", nullable = false)
    String storageFileName;

    @Column(name = "storage_path", nullable = false, length = 500)
    String storagePath;

    @Column(name = "file_extension", nullable = false, length = 20)
    String fileExtension;

    @Column(name = "mime_type", nullable = false, length = 100)
    String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    long fileSizeBytes;

    @Column(name = "sha256_checksum", nullable = false, length = 64)
    String sha256Checksum;

    @Column(name = "is_main_document", nullable = false)
    @Builder.Default
    boolean isMainDocument = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    UserLogin uploadedByUser;

    @Column(name = "delete_flg", nullable = false)
    @Builder.Default
    boolean deleteFlg = false;
}
