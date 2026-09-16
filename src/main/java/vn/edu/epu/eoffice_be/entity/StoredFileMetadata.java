package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.type.SqlTypes;
import vn.edu.epu.eoffice_be.common.entity.BaseEntity;

import java.util.Map;

/**
 * Ánh xạ siêu dữ liệu tệp của các backend lưu trữ (Strategy: S3, Telegram, Supabase...).
 * Thiết kế linh hoạt cho nhiều provider:
 * - Các trường chung (provider, fileId, tên, contentType, kích thước, checksum) nằm ở cột cố định.
 * - Các trường riêng biệt của từng provider (messageId của Telegram, bucket/region của S3,
 *   path của Supabase...) lưu trong cột {@code metadata} (jsonb) dạng key-value.
 * - Provider nào không có trường nào đó (vd: Telegram không có storagePath) để cột tương ứng null.
 * - Provider nào có trường dư (vd: S3 có versionId, eTag) đẩy vào metadata.
 * Nhờ vậy một bảng duy nhất phục vụ mọi provider mà không cần thêm cột cho từng loại.
 */
@Entity
@Table(name = "file_storage_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoredFileMetadata extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eoffice_seq")
    @SequenceGenerator(name = "eoffice_seq", sequenceName = "hibernate_sequence", allocationSize = 50)
    Long id;

    @Column(name = "provider", nullable = false, length = 50)
    String provider;

    @Column(name = "file_id", nullable = false, length = 512)
    String fileId;

    @Column(name = "original_file_name", length = 512)
    String originalFileName;

    @Column(name = "content_type", length = 100)
    String contentType;

    @Column(name = "size_bytes")
    Long sizeBytes;

    @Column(name = "sha256_checksum", length = 64)
    String sha256Checksum;

    @Column(name = "storage_path", length = 1024)
    String storagePath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    Map<String, String> metadata;

    @SoftDelete
    @Column(name = "delete_flg", nullable = false)
    @Builder.Default
    boolean deleteFlg = false;
}
