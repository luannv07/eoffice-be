package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.epu.eoffice_be.entity.DocumentWorkflowLog;
import java.util.List;

@Repository
public interface DocumentWorkflowLogRepository extends JpaRepository<DocumentWorkflowLog, Long> {
    List<DocumentWorkflowLog> findByDocumentIdOrderByCreatedAtAsc(Long documentId);
}