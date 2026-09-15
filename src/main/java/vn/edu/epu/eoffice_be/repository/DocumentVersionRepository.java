package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.epu.eoffice_be.entity.DocumentVersion;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    List<DocumentVersion> findByDocumentIdAndDeleteFlgFalseOrderByVersionNumberDesc(Long documentId);

    Optional<DocumentVersion> findByDocumentIdAndVersionNumberAndDeleteFlgFalse(Long documentId, int versionNumber);

    Optional<DocumentVersion> findTopByDocumentIdAndDeleteFlgFalseOrderByVersionNumberDesc(Long documentId);
}
