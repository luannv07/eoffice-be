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

    @Query("SELECT l FROM DocumentWorkflowLog l WHERE l.document.id = :documentId AND l.deleteFlg = false")
    Page<DocumentWorkflowLog> findByDocumentIdAndDeleteFlgFalse(@Param("documentId") Long documentId, Pageable pageable);

    @Query("SELECT l FROM DocumentWorkflowLog l WHERE l.document.id = :documentId AND l.deleteFlg = false ORDER BY l.createdDate ASC")
    List<DocumentWorkflowLog> findByDocumentIdAndDeleteFlgFalseOrderByCreatedDateAsc(@Param("documentId") Long documentId);

    @Query("SELECT l FROM DocumentWorkflowLog l WHERE l.document.id = :documentId AND l.action = :action AND l.deleteFlg = false")
    List<DocumentWorkflowLog> findByDocumentIdAndActionAndDeleteFlgFalse(@Param("documentId") Long documentId, @Param("action") WorkflowAction action);

    @Query("SELECT l FROM DocumentWorkflowLog l WHERE l.document.id = :documentId ORDER BY l.createdDate ASC")
    List<DocumentWorkflowLog> findByDocumentIdOrderByCreatedDateAsc(@Param("documentId") Long documentId);

    default List<DocumentWorkflowLog> findByDocumentIdOrderByCreatedAtAsc(Long documentId) {
        return findByDocumentIdOrderByCreatedDateAsc(documentId);
    }
}