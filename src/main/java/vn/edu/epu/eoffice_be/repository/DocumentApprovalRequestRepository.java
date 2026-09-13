package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.epu.eoffice_be.common.enums.ApprovalRequestStatus;
import vn.edu.epu.eoffice_be.entity.DocumentApprovalRequest;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentApprovalRequestRepository extends JpaRepository<DocumentApprovalRequest, Long> {

    List<DocumentApprovalRequest> findByDocumentIdAndDeleteFlgFalseOrderBySubmittedAtDesc(Long documentId);

    Optional<DocumentApprovalRequest> findTopByDocumentIdAndStatusAndDeleteFlgFalseOrderBySubmittedAtDesc(
            Long documentId, ApprovalRequestStatus status);

    Optional<DocumentApprovalRequest> findTopByDocumentIdAndDeleteFlgFalseOrderBySubmittedAtDesc(Long documentId);
}
