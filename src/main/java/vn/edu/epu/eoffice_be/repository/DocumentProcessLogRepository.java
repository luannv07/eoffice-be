package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.epu.eoffice_be.entity.DocumentProcessLog;

import java.util.List;

@Repository
public interface DocumentProcessLogRepository extends JpaRepository<DocumentProcessLog, Long> {

    List<DocumentProcessLog> findByDocumentIdAndDeleteFlgFalseOrderByLogTimeDesc(Long documentId);
}
