package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.epu.eoffice_be.entity.DocumentAttachment;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentAttachmentRepository extends JpaRepository<DocumentAttachment, Long> {

    List<DocumentAttachment> findByDocumentIdAndDeleteFlgFalse(Long documentId);

    Optional<DocumentAttachment> findByIdAndDocumentIdAndDeleteFlgFalse(Long id, Long documentId);

    List<DocumentAttachment> findByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(Long documentId);

    boolean existsByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(Long documentId);

    boolean existsByDocumentIdAndDeleteFlgFalse(Long documentId);
}
