package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.entity.RolePermission;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

	@Query("""
					SELECT DISTINCT rp.permissionCd FROM RolePermission rp
					WHERE rp.role = :role
					  AND rp.deleteFlg = false
					""")
	List<String> findPermissionCodesByRole(@Param("role") Role role);
}