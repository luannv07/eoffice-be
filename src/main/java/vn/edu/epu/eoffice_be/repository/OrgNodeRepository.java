package vn.edu.epu.eoffice_be.repository;

import vn.edu.epu.eoffice_be.entity.OrgNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrgNodeRepository extends JpaRepository<OrgNode, Long> {

    Optional<OrgNode> findByIdAndDeleteFlgFalse(Long id);

    Optional<OrgNode> findByCodeAndDeleteFlgFalse(String code);

    List<OrgNode> findAllByDeleteFlgFalseOrderByOrgOrderAsc();

    @Query("""
            SELECT n FROM OrgNode n
            JOIN OrgNodeClosure c ON c.descendant = n
            WHERE c.ancestor.id = :ancestorId
              AND n.deleteFlg = false
            ORDER BY n.orgOrder ASC
            """)
    List<OrgNode> findActiveDescendants(@Param("ancestorId") Long ancestorId);

    @Query("""
            SELECT n FROM OrgNode n
            JOIN OrgNodeClosure c ON c.ancestor = n
            WHERE c.descendant.id = :descendantId
              AND n.deleteFlg = false
            ORDER BY c.depth DESC
            """)
    List<OrgNode> findActiveAncestors(@Param("descendantId") Long descendantId);

    @Query("""
            SELECT DISTINCT n FROM OrgNode n
            JOIN OrgNodeClosure c ON c.descendant = n
            JOIN PositionAssignment pa ON pa.orgNode.id = c.ancestor.id
            WHERE pa.positionCd = :positionCd
              AND pa.deleteFlg = false
              AND pa.actualDismissalDate IS NULL
              AND n.deleteFlg = false
            ORDER BY n.orgOrder ASC
            """)
    List<OrgNode> findNodesReachableByPositionWithInheritance(@Param("positionCd") String positionCd);
}