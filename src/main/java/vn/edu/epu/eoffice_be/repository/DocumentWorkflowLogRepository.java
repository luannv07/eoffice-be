package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;
import vn.edu.epu.eoffice_be.entity.DocumentWorkflowLog;
import java.util.List;

@Repository
public interface DocumentWorkflowLogRepository extends JpaRepository<DocumentWorkflowLog, Long> {

    @Query("SELECT l FROM DocumentWorkflowLog l WHERE l.document.id = :documentId AND l.deleteFlg = false ORDER BY l.createdDate ASC, l.id ASC")
    Page<DocumentWorkflowLog> findByDocumentIdAndDeleteFlgFalse(@Param("documentId") Long documentId, Pageable pageable);

    @Query("SELECT l FROM DocumentWorkflowLog l WHERE l.document.id = :documentId AND l.deleteFlg = false ORDER BY l.createdDate ASC, l.id ASC")
    List<DocumentWorkflowLog> findByDocumentIdAndDeleteFlgFalseOrderByCreatedDateAsc(@Param("documentId") Long documentId);

    @Query("SELECT l FROM DocumentWorkflowLog l WHERE l.document.id = :documentId AND l.action = :action AND l.deleteFlg = false")
    List<DocumentWorkflowLog> findByDocumentIdAndActionAndDeleteFlgFalse(@Param("documentId") Long documentId, @Param("action") WorkflowAction action);

    @Query("SELECT l FROM DocumentWorkflowLog l WHERE l.document.id = :documentId AND l.deleteFlg = false ORDER BY l.createdDate ASC, l.id ASC")
    List<DocumentWorkflowLog> findByDocumentIdOrderByCreatedDateAsc(@Param("documentId") Long documentId);

    /**
     * Compatibility alias for {@link #findByDocumentIdOrderByCreatedDateAsc(Long)} or {@link #findByDocumentIdAndDeleteFlgFalseOrderByCreatedDateAsc(Long)}.
     * <p>
     * Excludes soft-deleted logs ({@code deleteFlg = false}) and orders chronologically by {@code createdDate} ASC, {@code id} ASC.
     * The actual persistent JPA property in {@link DocumentWorkflowLog} (inherited from
     * {@link vn.edu.epu.eoffice_be.common.entity.BaseEntity}) is {@code createdDate}, not {@code createdAt}.
     * </p>
     *
     * @param documentId id của văn bản
     * @return danh sách workflow log hoạt động (deleteFlg = false) theo thứ tự createdDate ASC, id ASC
     * @deprecated Use {@link #findByDocumentIdAndDeleteFlgFalseOrderByCreatedDateAsc(Long)} or {@link #findByDocumentIdOrderByCreatedDateAsc(Long)}.
     */
    @Deprecated
    default List<DocumentWorkflowLog> findByDocumentIdOrderByCreatedAtAsc(Long documentId) {
        return findByDocumentIdOrderByCreatedDateAsc(documentId);
    }
}