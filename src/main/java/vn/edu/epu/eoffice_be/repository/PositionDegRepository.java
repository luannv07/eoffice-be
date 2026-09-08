package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.epu.eoffice_be.entity.PositionDeg;

import java.time.Instant;
import java.util.List;

public interface PositionDegRepository extends JpaRepository<PositionDeg, Long> {

	List<PositionDeg> findByOrgNodeIdAndDeleteFlgFalse(Long orgNodeId);

	@Query("""
					SELECT COUNT(pd) > 0 FROM PositionDeg pd
					WHERE pd.delegatee.id = :delegateeUserId
					  AND pd.orgNode.id = :orgNodeId
					  AND pd.permissionCd = :permissionCd
					  AND pd.deleteFlg = false
					  AND pd.actualRevokedAt IS NULL
					  AND pd.effectiveFrom <= :atTime
					  AND pd.effectiveTo >= :atTime
					""")
	boolean hasValidDelegation(
					@Param("delegateeUserId") Long delegateeUserId,
					@Param("orgNodeId") Long orgNodeId,
					@Param("permissionCd") String permissionCd,
					@Param("atTime") Instant atTime);
}
