package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.epu.eoffice_be.entity.OrgNode;

import java.util.List;
import java.util.Optional;

public interface OrgNodeRepository extends JpaRepository<OrgNode, Long> {

	Optional<OrgNode> findByIdAndDeleteFlgFalse(Long id);

	Optional<OrgNode> findByCodeAndDeleteFlgFalse(String code);

	List<OrgNode> findAllByDeleteFlgFalseOrderByOrgOrderAsc();

	@Query(nativeQuery = true, value = """
					WITH RECURSIVE descendants AS (
					    SELECT id FROM org_nodes WHERE id = :ancestorId AND delete_flg = false
					    UNION ALL
					    SELECT n.id FROM org_nodes n
					    INNER JOIN descendants d ON n.parent_id = d.id
					    WHERE n.delete_flg = false
					)
					SELECT n.* FROM org_nodes n
					JOIN descendants ON descendants.id = n.id
					WHERE n.id <> :ancestorId
					ORDER BY n.org_order ASC
					""")
	List<OrgNode> findActiveDescendants(@Param("ancestorId") Long ancestorId);

	@Query(nativeQuery = true, value = """
					WITH RECURSIVE ancestors AS (
					    SELECT parent_id AS id FROM org_nodes WHERE id = :descendantId AND delete_flg = false
					    UNION ALL
					    SELECT n.parent_id FROM org_nodes n
					    INNER JOIN ancestors a ON n.id = a.id
					    WHERE n.parent_id IS NOT NULL AND n.delete_flg = false
					)
					SELECT n.* FROM org_nodes n
					JOIN ancestors ON ancestors.id = n.id
					WHERE n.delete_flg = false
					ORDER BY n.org_order ASC
					""")
	List<OrgNode> findActiveAncestors(@Param("descendantId") Long descendantId);

	@Query(nativeQuery = true, value = """
					WITH RECURSIVE reachable AS (
					    SELECT pa.org_node_id AS id
					    FROM position_assignments pa
					    WHERE pa.position_cd = :positionCd
					      AND pa.delete_flg = false
					      AND pa.actual_dismissal_date IS NULL
					    UNION ALL
					    SELECT child.id FROM org_nodes child
					    INNER JOIN reachable r ON child.parent_id = r.id
					    WHERE child.delete_flg = false
					)
					SELECT n.* FROM org_nodes n
					JOIN reachable ON reachable.id = n.id
					WHERE n.delete_flg = false
					ORDER BY n.org_order ASC
					""")
	List<OrgNode> findNodesReachableByPositionWithInheritance(@Param("positionCd") String positionCd);

	@Query(nativeQuery = true, value = """
					WITH RECURSIVE descendants AS (
					    SELECT id FROM org_nodes WHERE id = :ancestorId AND delete_flg = false
					    UNION ALL
					    SELECT n.id FROM org_nodes n
					    INNER JOIN descendants d ON n.parent_id = d.id
					    WHERE n.delete_flg = false
					)
					SELECT id FROM descendants WHERE id <> :ancestorId
					""")
	List<Long> findActiveDescendantIds(@Param("ancestorId") Long ancestorId);

	List<OrgNode> findAllByParentIdAndDeleteFlgFalse(Long parentId);
}