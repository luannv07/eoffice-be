package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.epu.eoffice_be.common.enums.ApprovalRequestStatus;
import vn.edu.epu.eoffice_be.entity.DocumentApprovalRequest;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentApprovalRequestRepository extends JpaRepository<DocumentApprovalRequest, Long> {

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
