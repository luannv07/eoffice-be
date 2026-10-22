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
	 * Dùng khi TẠO MỚI (excludeId = null): không loại trừ bản ghi nào.
	 */
	@Query("""
					SELECT COUNT(pa) > 0 FROM PositionAssignment pa
					WHERE pa.userLogin.id = :userId
					  AND pa.deleteFlg = false
					  AND pa.actualDismissalDate IS NULL
					  AND (pa.effectiveTo IS NULL OR :from IS NULL OR pa.effectiveTo >= :from)
					  AND (pa.effectiveFrom IS NULL OR :to IS NULL OR pa.effectiveFrom <= :to)
					""")
	boolean existsOverlappingAssignmentForNew(@Param("userId") Long userId,
	                                          @Param("from") Instant from,
	                                          @Param("to") Instant to);

	/**
	 * Dùng khi CẬP NHẬT (excludeId != null): loại trừ bản ghi hiện tại khỏi kiểm tra.
	 */
	@Query("""
					SELECT COUNT(pa) > 0 FROM PositionAssignment pa
					WHERE pa.userLogin.id = :userId
					  AND pa.deleteFlg = false
					  AND pa.actualDismissalDate IS NULL
					  AND pa.id <> :excludeId
					  AND (pa.effectiveTo IS NULL OR :from IS NULL OR pa.effectiveTo >= :from)
					  AND (pa.effectiveFrom IS NULL OR :to IS NULL OR pa.effectiveFrom <= :to)
					""")
	boolean existsOverlappingAssignmentExcluding(@Param("userId") Long userId,
	                                              @Param("from") Instant from,
	                                              @Param("to") Instant to,
	                                              @Param("excludeId") Long excludeId);

	/**
	 * Phương thức điều phối: tự động chọn query phù hợp dựa trên excludeId.
	 * Giữ nguyên chữ ký gốc để Service không cần thay đổi.
	 */
	default boolean existsOverlappingAssignment(Long userId, Instant from, Instant to, Long excludeId) {
		if (excludeId == null) {
			return existsOverlappingAssignmentForNew(userId, from, to);
		}
		return existsOverlappingAssignmentExcluding(userId, from, to, excludeId);
	}
}