package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.epu.eoffice_be.entity.PositionPermission;

import java.util.List;
import java.util.Optional;

public interface PositionPermissionRepository extends JpaRepository<PositionPermission, Long> {

	List<PositionPermission> findByPositionCdAndDeleteFlgFalse(String positionCd);

	Optional<PositionPermission> findByPositionCdAndPermissionCdAndDeleteFlgFalse(String positionCd, String permissionCd);
}
