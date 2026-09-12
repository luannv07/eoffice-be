package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.entity.Document;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByIdAndDeleteFlgFalse(Long id);

    Optional<Document> findByDraftCodeAndDeleteFlgFalse(String draftCode);

    boolean existsByDraftCodeAndDeleteFlgFalse(String draftCode);

    Page<Document> findByCreatorUserIdAndDeleteFlgFalse(Long creatorUserId, Pageable pageable);

    /**
     * Tìm kiếm và phân trang tài liệu theo các tiêu chí kết hợp.
     * <p>
     * <b>Lưu ý về escape ký tự đặc biệt (Issue 16):</b> Tham số {@code keyword} được ghép vào mẫu LIKE
     * với {@code ESCAPE '\\'}. Tầng nghiệp vụ (Service layer) bắt buộc phải escape trước các ký tự đại diện
     * ('%', '_', '\\') thành ('\\%', '\\_', '\\\\') trước khi gọi phương thức này.
     * </p>
     * <p>
     * <b>Lưu ý về hiệu năng (Issue 17 / Ticket PERF-DOC-001):</b> Mẫu LIKE tiền tố wildcard '%keyword%' và LOWER()
     * không tận dụng được standard B-Tree index và có thể gây full table scan trên tập dữ liệu lớn.
     * Đã lên kế hoạch chuyển đổi sang PostgreSQL Full-Text Search (tsvector + GIN index) hoặc functional index
     * theo ticket kỹ thuật PERF-DOC-001 khi quy mô dữ liệu mở rộng.
     * </p>
     */
    @Query("""
        SELECT d FROM Document d
        WHERE d.deleteFlg = false
          AND (:creatorUserId IS NULL OR d.creatorUser.id = :creatorUserId)
          AND (:status IS NULL OR d.status = :status)
          AND (:orgNodeId IS NULL OR d.issuingOrgNode.id = :orgNodeId)
          AND (:keyword IS NULL OR :keyword = ''
                OR LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
                OR LOWER(d.draftCode) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
                OR LOWER(CAST(d.summary AS string)) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\')
    """)
    Page<Document> searchDocuments(
            @Param("creatorUserId") Long creatorUserId,
            @Param("status") DocumentStatus status,
            @Param("orgNodeId") Long orgNodeId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
        SELECT d FROM Document d
        WHERE CAST(d.status AS string) IN (:statuses)
          AND d.deleteFlg = false
        ORDER BY d.id ASC
    """)
    List<Document> findByStatusIn(@Param("statuses") List<String> statuses);

    @Query("""
        SELECT d FROM Document d
        WHERE CAST(d.status AS string) IN (:statuses)
          AND d.deleteFlg = false
    """)
    Page<Document> findByStatusIn(@Param("statuses") List<String> statuses, Pageable pageable);

    @Query(value = """
        SELECT DISTINCT d.* FROM documents d
        JOIN document_distribution dd ON d.id = dd.document_id
        WHERE d.status = 'PUBLISHED'
          AND (dd.target_type = 'ALL' 
               OR (dd.target_type = 'ORG' AND (:orgId IS NOT NULL AND dd.target_id = :orgId))
               OR (dd.target_type = 'USER' AND (:userId IS NOT NULL AND dd.target_id = :userId)))
        ORDER BY d.issue_date DESC, d.id DESC
        """, 
        countQuery = """
        SELECT COUNT(DISTINCT d.id) FROM documents d
        JOIN document_distribution dd ON d.id = dd.document_id
        WHERE d.status = 'PUBLISHED'
          AND (dd.target_type = 'ALL' 
               OR (dd.target_type = 'ORG' AND (:orgId IS NOT NULL AND dd.target_id = :orgId))
               OR (dd.target_type = 'USER' AND (:userId IS NOT NULL AND dd.target_id = :userId)))
        """,
        nativeQuery = true)
    Page<Document> findPublishedDocumentsPaged(
            @Param("orgId") Long orgId, 
            @Param("userId") Long userId, 
            Pageable pageable);

    @Query(value = """
        SELECT DISTINCT d.* FROM documents d
        JOIN document_distribution dd ON d.id = dd.document_id
        WHERE d.status = 'PUBLISHED'
          AND (dd.target_type = 'ALL' 
               OR (dd.target_type = 'ORG' AND (:orgId IS NOT NULL AND dd.target_id = :orgId))
               OR (dd.target_type = 'USER' AND (:userId IS NOT NULL AND dd.target_id = :userId)))
        ORDER BY d.issue_date DESC, d.id DESC
        """, nativeQuery = true)
    List<Document> findPublishedDocumentsForUser(
            @Param("orgId") Long orgId, 
            @Param("userId") Long userId);

    default List<Document> findPublishedDocumentsForUser(Long orgId) {
        return findPublishedDocumentsForUser(orgId, null);
    }

    @Query(value = """
        SELECT DISTINCT d.* FROM documents d
        JOIN document_distribution dd ON d.id = dd.document_id
        WHERE d.id = :documentId
          AND d.status = 'PUBLISHED'
          AND (
               dd.target_type = 'ALL'
            OR (dd.target_type = 'ORG' AND :orgId IS NOT NULL AND dd.target_id = :orgId)
            OR (dd.target_type = 'USER' AND :userId IS NOT NULL AND dd.target_id = :userId)
          )
        LIMIT 1
        """, nativeQuery = true)
    Optional<Document> findAccessiblePublishedDocument(
            @Param("documentId") Long documentId,
            @Param("orgId") Long orgId,
            @Param("userId") Long userId);

    default Optional<Document> findAccessiblePublishedDocument(Long documentId, Long orgId) {
        return findAccessiblePublishedDocument(documentId, orgId, null);
    }
}
