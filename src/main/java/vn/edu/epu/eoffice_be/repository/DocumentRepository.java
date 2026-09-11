package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.entity.Document;

import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByIdAndDeleteFlgFalse(Long id);

    Optional<Document> findByDraftCodeAndDeleteFlgFalse(String draftCode);

    boolean existsByDraftCodeAndDeleteFlgFalse(String draftCode);

    Page<Document> findByCreatorUserIdAndDeleteFlgFalse(Long creatorUserId, Pageable pageable);

    @Query("""
        SELECT d FROM Document d
        WHERE d.deleteFlg = false
          AND (:creatorUserId IS NULL OR d.creatorUser.id = :creatorUserId)
          AND (:status IS NULL OR d.status = :status)
          AND (:orgNodeId IS NULL OR d.issuingOrgNode.id = :orgNodeId)
          AND (:keyword IS NULL OR :keyword = ''
                OR LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
                OR LOWER(d.draftCode) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
                OR LOWER(CAST(d.summary AS string)) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\')
    """)
    Page<Document> searchDocuments(
            @Param("creatorUserId") Long creatorUserId,
            @Param("status") DocumentStatus status,
            @Param("orgNodeId") Long orgNodeId,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
