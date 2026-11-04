package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import vn.edu.epu.eoffice_be.entity.DocumentAttachment;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentAttachmentRepository extends JpaRepository<DocumentAttachment, Long> {

    List<DocumentAttachment> findByDocumentIdAndDeleteFlgFalse(Long documentId);

    Optional<DocumentAttachment> findByIdAndDocumentIdAndDeleteFlgFalse(Long id, Long documentId);

    List<DocumentAttachment> findByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(Long documentId);

    boolean existsByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(Long documentId);

    /** Flush pending changes before retirement; reload entities after the bulk update clears the context. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update DocumentAttachment a set a.deleteFlg = true, a.isMainDocument = false "
            + "where a.document.id = :documentId and a.deleteFlg = false and a.isMainDocument = true")
    int retireCurrentMain(@Param("documentId") Long documentId);

    /** Return checkpoints also cover records created before current-main retirement was introduced. */
    @Query(value = """
            select max(r.returned_at) from (
                select log_time as returned_at from document_process_logs
                  where document_id = :documentId and delete_flg = false and action = 'RECALL_APPROVAL'
                union all
                select created_date as returned_at from document_workflow_logs
                  where document_id = :documentId and delete_flg = false and action = 'REQUEST_REVISION'
            ) r
            """, nativeQuery = true)
    Optional<Instant> findLatestPdfReturnAt(@Param("documentId") Long documentId);

    boolean existsByDocumentIdAndDeleteFlgFalse(Long documentId);
}
