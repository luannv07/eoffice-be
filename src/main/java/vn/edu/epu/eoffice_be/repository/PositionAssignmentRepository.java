package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.epu.eoffice_be.entity.PositionAssignment;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public interface PositionAssignmentRepository extends JpaRepository<PositionAssignment, Long> {

	List<PositionAssignment> findByUserLoginIdAndDeleteFlgFalse(Long userLoginId);

	List<PositionAssignment> findByOrgNodeIdAndDeleteFlgFalse(Long orgNodeId);

	Page<PositionAssignment> findByUserLoginIdAndDeleteFlgFalse(Long userLoginId, Pageable pageable);

	Page<PositionAssignment> findByOrgNodeIdAndDeleteFlgFalse(Long orgNodeId, Pageable pageable);

	@Query("""
					SELECT pa FROM PositionAssignment pa
					WHERE pa.userLogin.id = :userId
					  AND pa.deleteFlg = false
					  AND pa.actualDismissalDate IS NULL
					  AND pa.effectiveFrom <= :atTime
					  AND (pa.effectiveTo IS NULL OR pa.effectiveTo >= :atTime)
					""")
	Page<PositionAssignment> findActiveAssignments(@Param("userId") Long userId, @Param("atTime") Instant atTime, Pageable pageable);

	@Query("""
					SELECT DISTINCT pa FROM PositionAssignment pa
					WHERE pa.userLogin.id = :userId
					  AND pa.deleteFlg = false
					  AND pa.actualDismissalDate IS NULL
					  AND pa.effectiveFrom <= :atTime
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

	@Query("""
					SELECT pa FROM PositionAssignment pa
					WHERE pa.deleteFlg = false
					  AND pa.actualDismissalDate IS NULL
					  AND (pa.effectiveFrom IS NULL OR pa.effectiveFrom <= :atTime)
					  AND (pa.effectiveTo IS NULL OR pa.effectiveTo >= :atTime)
					  AND pa.positionCd IN :positionCds
					""")
	List<PositionAssignment> findActiveAssignmentsByPositionCdsInternal(
					@Param("positionCds") Collection<String> positionCds,
					@Param("atTime") Instant atTime);

	default List<PositionAssignment> findActiveAssignmentsByPositionCds(
					Collection<String> positionCds,
					Instant atTime) {
		if (positionCds == null || positionCds.isEmpty()) {
			return Collections.emptyList();
		}
		return findActiveAssignmentsByPositionCdsInternal(positionCds, atTime);
	}

	boolean existsByUserLoginIdAndDeleteFlgFalse(Long userLoginId);

	/**
	 * Tạo mới có thời hạn (to != null, excludeId = null)
	 */
	@Query("""
					SELECT COUNT(pa) > 0 FROM PositionAssignment pa
					WHERE pa.userLogin.id = :userId
					  AND pa.deleteFlg = false
					  AND pa.actualDismissalDate IS NULL
					  AND (pa.effectiveTo IS NULL OR pa.effectiveTo >= :from)
					  AND pa.effectiveFrom <= :to
					""")
	boolean existsOverlappingWithTo(@Param("userId") Long userId,
	                                @Param("from") Instant from,
	                                @Param("to") Instant to);

	/**
	 * Tạo mới vô thời hạn (to == null, excludeId = null)
	 */
	@Query("""
					SELECT COUNT(pa) > 0 FROM PositionAssignment pa
					WHERE pa.userLogin.id = :userId
					  AND pa.deleteFlg = false
					  AND pa.actualDismissalDate IS NULL
					  AND (pa.effectiveTo IS NULL OR pa.effectiveTo >= :from)
					""")
	boolean existsOverlappingOpenEnded(@Param("userId") Long userId,
	                                  @Param("from") Instant from);

	/**
	 * Cập nhật có thời hạn (to != null, excludeId != null)
	 */
	@Query("""
					SELECT COUNT(pa) > 0 FROM PositionAssignment pa
					WHERE pa.userLogin.id = :userId
					  AND pa.deleteFlg = false
					  AND pa.actualDismissalDate IS NULL
					  AND pa.id <> :excludeId
					  AND (pa.effectiveTo IS NULL OR pa.effectiveTo >= :from)
					  AND pa.effectiveFrom <= :to
					""")
	boolean existsOverlappingWithToExcluding(@Param("userId") Long userId,
	                                         @Param("from") Instant from,
	                                         @Param("to") Instant to,
	                                         @Param("excludeId") Long excludeId);

	/**
	 * Cập nhật vô thời hạn (to == null, excludeId != null)
	 */
	@Query("""
					SELECT COUNT(pa) > 0 FROM PositionAssignment pa
					WHERE pa.userLogin.id = :userId
					  AND pa.deleteFlg = false
					  AND pa.actualDismissalDate IS NULL
					  AND pa.id <> :excludeId
					  AND (pa.effectiveTo IS NULL OR pa.effectiveTo >= :from)
					""")
	boolean existsOverlappingOpenEndedExcluding(@Param("userId") Long userId,
	                                            @Param("from") Instant from,
	                                            @Param("excludeId") Long excludeId);

	/**
	 * Phương thức điều phối: tự động chọn query phù hợp dựa trên to và excludeId.
	 * Hoàn toàn không sử dụng biểu thức ':param IS NULL' trong SQL, tương thích 100% PostgreSQL.
	 */
	default boolean existsOverlappingAssignment(Long userId, Instant from, Instant to, Long excludeId) {
		Instant effectiveFrom = from != null ? from : Instant.now();
		if (excludeId == null) {
			if (to == null) {
				return existsOverlappingOpenEnded(userId, effectiveFrom);
			} else {
				return existsOverlappingWithTo(userId, effectiveFrom, to);
			}
		} else {
			if (to == null) {
				return existsOverlappingOpenEndedExcluding(userId, effectiveFrom, excludeId);
			} else {
				return existsOverlappingWithToExcluding(userId, effectiveFrom, to, excludeId);
			}
		}
	}
}