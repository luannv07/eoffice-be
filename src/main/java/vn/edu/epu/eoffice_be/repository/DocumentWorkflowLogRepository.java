package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;
import vn.edu.epu.eoffice_be.entity.DocumentWorkflowLog;

import java.time.Instant;
import java.util.List;

@Repository
public interface DocumentWorkflowLogRepository extends JpaRepository<DocumentWorkflowLog, Long> {

    @Query("SELECT l FROM DocumentWorkflowLog l WHERE l.document.id = :documentId AND (l.deleteFlg = false OR l.deleteFlg IS NULL) ORDER BY l.createdDate ASC, l.id ASC")
    List<DocumentWorkflowLog> findByDocumentIdOrderByCreatedDateAsc(@Param("documentId") Long documentId);

    // Đối chiếu hash với log ký nháy của đúng hồ sơ, người ký và lượt thẩm định.
    @Query("""
            SELECT COUNT(l) > 0 FROM DocumentWorkflowLog l
            WHERE l.document.id = :documentId AND l.approvalRequest.id = :requestId
              AND l.actor.id = :actorId AND l.signatureHash = :hash AND l.deleteFlg = false
              AND l.action = vn.edu.epu.eoffice_be.common.enums.WorkflowAction.INITIAL_SIGN
              AND l.fromStatus = vn.edu.epu.eoffice_be.common.enums.DocumentStatus.IN_REVIEW
              AND l.toStatus = vn.edu.epu.eoffice_be.common.enums.DocumentStatus.IN_REVIEW
            """)
    boolean existsReviewSignature(@Param("documentId") Long documentId, @Param("requestId") Long requestId,
                                  @Param("actorId") Long actorId, @Param("hash") String hash);

    // Tìm lần bàn giao đã đóng lượt thẩm định tại thời điểm mở yêu cầu lãnh đạo.
    @Query("""
            SELECT COUNT(l) > 0 FROM DocumentWorkflowLog l
            WHERE l.document.id = :documentId AND l.actor.id = :actorId AND l.deleteFlg = false
              AND l.action = :action AND l.approvalRequest.processedAt = :processedAt
            """)
    boolean existsLinkedDecision(@Param("documentId") Long documentId, @Param("action") WorkflowAction action,
                                 @Param("actorId") Long actorId, @Param("processedAt") Instant processedAt);

    @Deprecated(since = "1.0.0", forRemoval = true)
    @Query("SELECT l FROM DocumentWorkflowLog l WHERE l.document.id = :documentId AND (l.deleteFlg = false OR l.deleteFlg IS NULL) ORDER BY l.createdDate ASC, l.id ASC")
    List<DocumentWorkflowLog> findByDocumentIdOrderByCreatedAtAsc(@Param("documentId") Long documentId);

    @Query("SELECT l FROM DocumentWorkflowLog l WHERE l.document.id = :documentId AND (l.deleteFlg = false OR l.deleteFlg IS NULL) ORDER BY l.createdDate ASC, l.id ASC")
    List<DocumentWorkflowLog> findByDocumentIdAndDeleteFlgFalseOrderByCreatedDateAsc(@Param("documentId") Long documentId);

    @Query(value = "SELECT l FROM DocumentWorkflowLog l WHERE l.document.id = :documentId AND (l.deleteFlg = false OR l.deleteFlg IS NULL)",
           countQuery = "SELECT count(l) FROM DocumentWorkflowLog l WHERE l.document.id = :documentId AND (l.deleteFlg = false OR l.deleteFlg IS NULL)")
    Page<DocumentWorkflowLog> findByDocumentIdAndDeleteFlgFalse(@Param("documentId") Long documentId, Pageable pageable);

}
