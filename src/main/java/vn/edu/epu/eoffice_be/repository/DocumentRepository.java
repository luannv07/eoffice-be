package vn.edu.epu.eoffice_be.repository;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.entity.Document;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {

    Optional<Document> findByIdAndDeleteFlgFalse(Long id);

    /** Correlated EXISTS preserves paging/counts and avoids per-document participant queries. */
    static Specification<Document> readableDrafts(Long userId, boolean legacyGlobal, boolean adminReadAll,
                                                  Collection<DocumentStatus> guardedStatuses) {
        if (guardedStatuses == null || guardedStatuses.isEmpty()) {
            throw new IllegalArgumentException("guardedStatuses must not be null or empty");
        }
        return (root, query, cb) -> {
            if (adminReadAll) return cb.isFalse(root.get("deleteFlg"));
            var participation = query.subquery(Long.class);
            var request = participation.from(vn.edu.epu.eoffice_be.entity.DocumentApprovalRequest.class);
            participation.select(request.get("id")).where(
                    cb.equal(request.get("document").get("id"), root.get("id")),
                    cb.isFalse(request.get("deleteFlg")),
                    cb.or(cb.equal(request.get("senderUser").get("id"), userId),
                            cb.equal(request.get("approverUser").get("id"), userId)));
            var owner = cb.equal(root.get("drafter").get("id"), userId);
            var guarded = root.get("status").in(guardedStatuses);
            return cb.and(cb.isFalse(root.get("deleteFlg")), cb.or(
                    cb.and(guarded, cb.or(owner, cb.exists(participation))),
                    cb.and(cb.not(guarded), legacyGlobal ? cb.conjunction() : owner)));
        };
    }

    default Page<Document> searchReadableDrafts(Long userId, boolean legacyGlobal, boolean adminReadAll,
            Collection<DocumentStatus> guardedStatuses, DocumentStatus status, Long orgNodeId,
            String keyword, Pageable pageable) {
        Specification<Document> filter = (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (status != null) conditions.add(cb.equal(root.get("status"), status));
            if (orgNodeId != null) conditions.add(cb.equal(root.get("issuingOrgNode").get("id"), orgNodeId));
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                conditions.add(cb.or(cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("draftCode")), pattern, '\\'),
                        cb.like(cb.lower(root.get("summary")), pattern, '\\')));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
        return findAll(readableDrafts(userId, legacyGlobal, adminReadAll, guardedStatuses).and(filter), pageable);
    }

    @Query("SELECT d FROM Document d WHERE d.deleteFlg = false AND (d.primaryFilePath = :reference OR d.officialFilePath = :reference)")
    List<Document> findReferencingFile(@Param("reference") String reference);

    @Query("""
        SELECT d FROM Document d WHERE d.deleteFlg = false
          AND (d.id IN :documentIds OR d.primaryFilePath IN :references OR d.officialFilePath IN :references)
        """)
    List<Document> findFileLinkedDocuments(@Param("documentIds") Collection<Long> documentIds,
                                           @Param("references") Collection<String> references);

    Optional<Document> findByDraftCodeAndDeleteFlgFalse(String draftCode);

    boolean existsByDraftCodeAndDeleteFlgFalse(String draftCode);
 
    boolean existsByOfficialNumberAndIdNotAndDeleteFlgFalse(String officialNumber, Long id);

    boolean existsByOfficialNumberAndDeleteFlgFalse(String officialNumber);

    Page<Document> findByDrafterIdAndDeleteFlgFalse(Long drafterId, Pageable pageable);

    /**
     * Tìm kiếm và phân trang tài liệu theo các tiêu chí kết hợp bằng JPA Specification.
     * <p>
     * Tránh lỗi PostgreSQL 'could not determine data type of parameter' khi tham số là null
     * bằng cách chỉ bổ sung Predicate khi tham số thực sự có giá trị (non-null / non-blank).
     * </p>
     * <p>
     * <b>Lưu ý về escape ký tự đặc biệt (Issue 16):</b> Tham số {@code keyword} được escape
     * trước các ký tự đại diện ('%', '_', '\\') từ tầng Service và so khớp LIKE với ký tự escape '\\'.
     * </p>
     */
    default Page<Document> searchDocuments(
            Long drafterId,
            DocumentStatus status,
            Long orgNodeId,
            String keyword,
            Pageable pageable
    ) {
        Specification<Document> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleteFlg")));

            if (drafterId != null) {
                predicates.add(cb.equal(root.get("drafter").get("id"), drafterId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (orgNodeId != null) {
                predicates.add(cb.equal(root.get("issuingOrgNode").get("id"), orgNodeId));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                Predicate titlePred = cb.like(cb.lower(root.get("title")), pattern, '\\');
                Predicate draftCodePred = cb.like(cb.lower(root.get("draftCode")), pattern, '\\');
                Predicate summaryPred = cb.like(cb.lower(root.get("summary")), pattern, '\\');
                predicates.add(cb.or(titlePred, draftCodePred, summaryPred));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return findAll(spec, pageable);
    }

    List<Document> findByStatusAndDeleteFlgFalse(DocumentStatus status);

    @Query("""
        SELECT d FROM Document d 
        LEFT JOIN d.documentBook db
        WHERE d.status = :status 
          AND d.deleteFlg = false 
          AND (:documentBookId IS NULL OR db.id = :documentBookId)
        ORDER BY d.issueDate DESC, d.id DESC
    """)
    List<Document> findByStatusOrderByIssueDateDescIdDesc(
            @Param("status") DocumentStatus status,
            @Param("documentBookId") Long documentBookId);

    default List<Document> findByStatusOrderByIssueDateDescIdDesc(DocumentStatus status) {
        return findByStatusOrderByIssueDateDescIdDesc(status, null);
    }

    /**
     * Phân trang văn bản theo trạng thái đơn, loại trừ bản xóa mềm.
     *
     * @param status   trạng thái cần lọc
     * @param pageable thông tin phân trang
     * @return trang văn bản thỏa mãn
     */
    Page<Document> findByStatusAndDeleteFlgFalse(DocumentStatus status, Pageable pageable);

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
        JOIN document_distributions dd ON d.id = dd.document_id
        WHERE d.status = 'PUBLISHED'
          AND (dd.target_type = 'ALL' 
               OR (dd.target_type = 'ORG' AND (:orgId IS NOT NULL AND dd.target_id = :orgId))
               OR (dd.target_type = 'USER' AND (:userId IS NOT NULL AND dd.target_id = :userId)))
        ORDER BY d.issue_date DESC, d.id DESC
        """, 
        countQuery = """
        SELECT COUNT(DISTINCT d.id) FROM documents d
        JOIN document_distributions dd ON d.id = dd.document_id
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
        JOIN document_distributions dd ON d.id = dd.document_id
        WHERE d.status = 'PUBLISHED'
          AND (:documentBookId IS NULL OR d.document_book_id = :documentBookId)
          AND (dd.target_type = 'ALL' 
               OR (dd.target_type = 'ORG' AND (:orgId IS NOT NULL AND dd.target_id = :orgId))
               OR (dd.target_type = 'USER' AND (:userId IS NOT NULL AND dd.target_id = :userId)))
        ORDER BY d.issue_date DESC, d.id DESC
        """, nativeQuery = true)
    List<Document> findPublishedDocumentsForUser(
            @Param("orgId") Long orgId, 
            @Param("userId") Long userId,
            @Param("documentBookId") Long documentBookId);

    default List<Document> findPublishedDocumentsForUser(Long orgId, Long userId) {
        return findPublishedDocumentsForUser(orgId, userId, null);
    }

    default List<Document> findPublishedDocumentsForUser(Long orgId) {
        return findPublishedDocumentsForUser(orgId, null, null);
    }

    @Query(value = """
        SELECT DISTINCT d.* FROM documents d
        JOIN document_distributions dd ON d.id = dd.document_id
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

    /**
     * BE-04: Lấy danh sách văn bản đã được cấp số của một sổ văn bản.
     * <p>
     * <b>Lưu ý về escape ký tự đặc biệt:</b> Tham số {@code keyword} được ghép vào mẫu LIKE
     * với {@code ESCAPE '\\'}. Tầng nghiệp vụ (Service layer) bắt buộc phải escape trước các ký tự đại diện
     * ('\\', '%', '_') thành ('\\\\', '\\%', '\\_') trước khi gọi phương thức này.
     * Xem: {@link vn.edu.epu.eoffice_be.service.impl.DocumentBookServiceImpl#getAssignedDocuments}
     * </p>
     */
    @Query("""
        SELECT d FROM Document d
        WHERE d.deleteFlg = false
          AND d.officialNumber IS NOT NULL
          AND (
            d.documentBook.id = :bookId
            OR (
                d.documentBook IS NULL
                AND (:typeCd IS NOT NULL AND UPPER(d.documentTypeCd) = UPPER(:typeCd))
                AND (:orgId IS NOT NULL AND (d.issuingOrgNode.id = :orgId OR d.issuingOrg.id = :orgId))
            )
          )
          AND (:status IS NULL OR d.status = :status)
          AND (:keyword IS NULL OR :keyword = ''
                OR LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
                OR LOWER(d.officialNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
                OR LOWER(d.draftCode) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\')
        ORDER BY d.id DESC
    """)
    Page<Document> findAssignedDocuments(
            @Param("bookId") Long bookId,
            @Param("typeCd") String typeCd,
            @Param("orgId") Long orgId,
            @Param("status") DocumentStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
