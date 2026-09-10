package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.entity.Document;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByIdAndDeleteFlgFalse(Long id);

    List<Document> findByStatusAndDeleteFlgFalse(DocumentStatus status);

    List<Document> findByDrafterIdAndDeleteFlgFalse(Long drafterId);
}