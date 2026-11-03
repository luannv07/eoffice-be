package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import vn.edu.epu.eoffice_be.common.enums.ApprovalRequestStatus;
import vn.edu.epu.eoffice_be.entity.DocumentApprovalRequest;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentApprovalRequestRepository extends JpaRepository<DocumentApprovalRequest, Long> {

    Optional<DocumentApprovalRequest> findFirstByDocumentIdOrderBySubmittedAtDescIdDesc(Long documentId);

    // The latest request is determined across all states, including soft-deleted requests.
    @org.springframework.data.jpa.repository.Query(value = """
        SELECT r FROM DocumentApprovalRequest r JOIN FETCH r.document d
        WHERE d.deleteFlg = false AND d.status = vn.edu.epu.eoffice_be.common.enums.DocumentStatus.IN_REVIEW
          AND r.deleteFlg = false AND r.status = vn.edu.epu.eoffice_be.common.enums.ApprovalRequestStatus.PENDING
          AND r.submittedAt IS NOT NULL AND r.approverUser.id = :userId
          AND NOT EXISTS (SELECT newer.id FROM DocumentApprovalRequest newer
              WHERE newer.document.id = d.id AND (newer.submittedAt IS NULL
                OR newer.submittedAt > r.submittedAt
                OR (newer.submittedAt = r.submittedAt AND newer.id > r.id)))
        ORDER BY r.submittedAt DESC, r.id DESC
        """, countQuery = """
        SELECT COUNT(r) FROM DocumentApprovalRequest r JOIN r.document d
        WHERE d.deleteFlg = false AND d.status = vn.edu.epu.eoffice_be.common.enums.DocumentStatus.IN_REVIEW
          AND r.deleteFlg = false AND r.status = vn.edu.epu.eoffice_be.common.enums.ApprovalRequestStatus.PENDING
          AND r.submittedAt IS NOT NULL AND r.approverUser.id = :userId
          AND NOT EXISTS (SELECT newer.id FROM DocumentApprovalRequest newer
              WHERE newer.document.id = d.id AND (newer.submittedAt IS NULL
                OR newer.submittedAt > r.submittedAt
                OR (newer.submittedAt = r.submittedAt AND newer.id > r.id)))
        """)
    Page<DocumentApprovalRequest> findReviewInbox(@org.springframework.data.repository.query.Param("userId") Long userId,
                                                 Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
        SELECT COUNT(r) > 0 FROM DocumentApprovalRequest r
        WHERE r.document.id = :documentId AND r.deleteFlg = false
          AND (r.senderUser.id = :userId OR r.approverUser.id = :userId)
        """)
    boolean existsParticipant(@org.springframework.data.repository.query.Param("documentId") Long documentId,
                              @org.springframework.data.repository.query.Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Query("""
        SELECT DISTINCT r.document.id FROM DocumentApprovalRequest r
        WHERE r.document.id IN :documentIds AND r.deleteFlg = false
          AND (r.senderUser.id = :userId OR r.approverUser.id = :userId)
        """)
    List<Long> findParticipantDocumentIds(
            @org.springframework.data.repository.query.Param("documentIds") java.util.Collection<Long> documentIds,
            @org.springframework.data.repository.query.Param("userId") Long userId);

    List<DocumentApprovalRequest> findByDocumentIdAndDeleteFlgFalseOrderBySubmittedAtDesc(Long documentId);

    Optional<DocumentApprovalRequest> findTopByDocumentIdAndStatusAndDeleteFlgFalseOrderBySubmittedAtDesc(
            Long documentId, ApprovalRequestStatus status);

    Optional<DocumentApprovalRequest> findTopByDocumentIdAndDeleteFlgFalseOrderBySubmittedAtDesc(Long documentId);
}
