package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;
import vn.edu.epu.eoffice_be.entity.DocumentWorkflowLog;
import java.util.List;

@Repository
public interface DocumentWorkflowLogRepository extends JpaRepository<DocumentWorkflowLog, Long> {
    Page<DocumentWorkflowLog> findByDocumentIdAndDeleteFlgFalse(Long documentId, Pageable pageable);
    List<DocumentWorkflowLog> findByDocumentIdAndDeleteFlgFalseOrderByCreatedDateAsc(Long documentId);
    List<DocumentWorkflowLog> findByDocumentIdAndActionAndDeleteFlgFalse(Long documentId, WorkflowAction action);
    List<DocumentWorkflowLog> findByDocumentIdOrderByCreatedDateAsc(Long documentId);

    default List<DocumentWorkflowLog> findByDocumentIdOrderByCreatedAtAsc(Long documentId) {
        return findByDocumentIdOrderByCreatedDateAsc(documentId);
    }
}