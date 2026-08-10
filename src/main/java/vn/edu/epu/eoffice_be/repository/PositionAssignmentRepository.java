package vn.edu.epu.eoffice_be.repository;

import vn.edu.epu.eoffice_be.entity.PositionAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PositionAssignmentRepository extends JpaRepository<PositionAssignment, Long> {

    List<PositionAssignment> findByUserLoginIdAndDeleteFlgFalse(Long userLoginId);

    List<PositionAssignment> findByOrgNodeIdAndDeleteFlgFalse(Long orgNodeId);

    @Query("""
            SELECT DISTINCT pa FROM PositionAssignment pa
            WHERE pa.userLogin.id = :userId
              AND pa.deleteFlg = false
              AND pa.actualDismissalDate IS NULL
              AND (pa.effectiveFrom IS NULL OR pa.effectiveFrom <= :atTime)
              AND (pa.effectiveTo IS NULL OR pa.effectiveTo >= :atTime)
            """)
    List<PositionAssignment> findActiveAssignments(@Param("userId") Long userId, @Param("atTime") Instant atTime);

    @Query("""
            SELECT DISTINCT pa FROM PositionAssignment pa
            WHERE pa.userLogin.id = :userId
              AND pa.orgNode.id = :orgNodeId
              AND pa.deleteFlg = false
              AND pa.actualDismissalDate IS NULL
            """)
    List<PositionAssignment> findActiveAssignmentsForOrgNode(
            @Param("userId") Long userId,
            @Param("orgNodeId") Long orgNodeId);

    boolean existsByUserLoginIdAndDeleteFlgFalse(Long userLoginId);
}