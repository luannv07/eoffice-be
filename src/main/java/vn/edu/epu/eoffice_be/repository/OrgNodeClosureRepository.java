package vn.edu.epu.eoffice_be.repository;

import vn.edu.epu.eoffice_be.entity.OrgNodeClosure;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrgNodeClosureRepository extends JpaRepository<OrgNodeClosure, OrgNodeClosure.OrgNodeClosureId> {

    List<OrgNodeClosure> findByAncestorId(Long ancestorId);

    List<OrgNodeClosure> findByDescendantId(Long descendantId);

    void deleteByDescendantId(Long descendantId);

    @Query("""
            SELECT DISTINCT c.ancestor.id FROM OrgNodeClosure c
            WHERE c.descendant.id IN :descendantIds
            """)
    List<Long> findAncestorIdsOf(@Param("descendantIds") List<Long> descendantIds);

    @Query("""
            SELECT c.descendant.id FROM OrgNodeClosure c
            WHERE c.ancestor.id = :ancestorId
            """)
    List<Long> findDescendantIdsOf(@Param("ancestorId") Long ancestorId);
}