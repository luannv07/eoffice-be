package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.epu.eoffice_be.entity.DocumentDistribution;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentDistributionRepository extends JpaRepository<DocumentDistribution, Long> {
    Optional<DocumentDistribution> findByDocumentIdAndTargetId(Long documentId, Long targetId);
    List<DocumentDistribution> findByDocumentId(Long documentId);
}