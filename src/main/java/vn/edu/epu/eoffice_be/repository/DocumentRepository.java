package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.entity.Document;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByIdAndDeleteFlgFalse(Long id);

    List<Document> findByStatusAndDeleteFlgFalse(DocumentStatus status);

    @Query("SELECT d FROM Document d WHERE d.drafter.id = :drafterId AND d.deleteFlg = false")
    List<Document> findByDrafterIdAndDeleteFlgFalse(@Param("drafterId") Long drafterId);

    /**
     * Canonical query to find active documents by statuses, ordered deterministically by id ASC.
     * Excludes soft-deleted documents (deleteFlg = false).
     *
     * @param statuses danh sách trạng thái cần lọc
     * @return danh sách văn bản thỏa mãn
     */
    @Query("SELECT d FROM Document d WHERE d.status IN :statuses AND d.deleteFlg = false ORDER BY d.id ASC")
    List<Document> findByStatusInAndDeleteFlgFalse(@Param("statuses") Collection<DocumentStatus> statuses);

    /**
     * Canonical paginated query to find active documents by statuses with deterministic ordering (ORDER BY d.id ASC).
     * Excludes soft-deleted documents (deleteFlg = false).
     *
     * @param statuses danh sách trạng thái cần lọc
     * @param pageable thông tin phân trang
     * @return trang văn bản thỏa mãn
     */
    @Query("SELECT d FROM Document d WHERE d.status IN :statuses AND d.deleteFlg = false ORDER BY d.id ASC")
    Page<Document> findByStatusInAndDeleteFlgFalse(@Param("statuses") Collection<DocumentStatus> statuses, Pageable pageable);

    /**
     * Backward-compatible alias for {@link #findByStatusInAndDeleteFlgFalse(Collection)}.
     * Excludes soft-deleted documents (deleteFlg = false).
     *
     * @param statuses danh sách trạng thái cần lọc
     * @return danh sách văn bản thỏa mãn
     */
    default List<Document> findByStatusIn(Collection<DocumentStatus> statuses) {
        return findByStatusInAndDeleteFlgFalse(statuses);
    }

    /**
     * Backward-compatible alias for {@link #findByStatusInAndDeleteFlgFalse(Collection, Pageable)}.
     * Excludes soft-deleted documents (deleteFlg = false).
     *
     * @param statuses danh sách trạng thái cần lọc
     * @param pageable thông tin phân trang
     * @return trang văn bản thỏa mãn
     */
    default Page<Document> findByStatusIn(Collection<DocumentStatus> statuses, Pageable pageable) {
        return findByStatusInAndDeleteFlgFalse(statuses, pageable);
    }

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