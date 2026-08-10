package vn.edu.epu.eoffice_be.repository;

import vn.edu.epu.eoffice_be.entity.PositionPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PositionPermissionRepository extends JpaRepository<PositionPermission, Long> {

    List<PositionPermission> findByOrgNodeIdAndDeleteFlgFalse(Long orgNodeId);

    List<PositionPermission> findByPositionCdAndDeleteFlgFalse(String positionCd);

    @Query("""
            SELECT DISTINCT p.permissionCd FROM PositionPermission p
            WHERE p.orgNode.id = :orgNodeId
              AND p.positionCd = :positionCd
              AND p.grantFlg = true
              AND p.deleteFlg = false
            """)
    List<String> findGrantedPermissionCodes(
            @Param("orgNodeId") Long orgNodeId,
            @Param("positionCd") String positionCd);

    @Query("""
            SELECT DISTINCT p.permissionCd FROM PositionPermission p
            JOIN OrgNodeClosure c ON c.ancestor.id = p.orgNode.id
            WHERE c.descendant.id = :descendantOrgNodeId
              AND p.positionCd = :positionCd
              AND p.grantFlg = true
              AND p.inheritToChildren = true
              AND p.deleteFlg = false
            """)
    List<String> findInheritedGrantedPermissionCodes(
            @Param("descendantOrgNodeId") Long descendantOrgNodeId,
            @Param("positionCd") String positionCd);
}