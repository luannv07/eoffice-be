package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.epu.eoffice_be.entity.Document;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Query(value = """
        SELECT DISTINCT d.* FROM documents d
        JOIN document_distributions dd ON d.id = dd.document_id
        WHERE d.status = 'PUBLISHED'
          AND (dd.target_type = 'ALL' OR (dd.target_type = 'ORG' AND dd.target_id = :orgId))
        ORDER BY d.issue_date DESC, d.id DESC
        """, nativeQuery = true)
    List<Document> findPublishedDocumentsForUser(@Param("orgId") Long orgId);

    @Query(value = """
        SELECT DISTINCT d.* FROM documents d
        JOIN document_distributions dd ON d.id = dd.document_id
        WHERE d.id = :documentId
          AND d.status = 'PUBLISHED'
          AND (dd.target_type = 'ALL' OR (dd.target_type = 'ORG' AND dd.target_id = :orgId))
        """, nativeQuery = true)
    Optional<Document> findAccessiblePublishedDocument(@Param("documentId") Long documentId, @Param("orgId") Long orgId);
}