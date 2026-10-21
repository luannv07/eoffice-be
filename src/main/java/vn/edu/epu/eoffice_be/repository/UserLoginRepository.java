package vn.edu.epu.eoffice_be.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.entity.UserLogin;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserLoginRepository extends JpaRepository<UserLogin, Long>, JpaSpecificationExecutor<UserLogin> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserLogin u where u.id = :id and u.deleteFlg = false")
    Optional<UserLogin> lockForPkiIssuance(@Param("id") Long id);

	Optional<UserLogin> findByUsernameAndDeleteFlgFalse(String username);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<UserLogin> findByEmailAndDeleteFlgFalse(String email);

	Optional<UserLogin> findByIdAndDeleteFlgFalse(Long id);

	boolean existsByUsernameAndDeleteFlgFalse(String username);

	boolean existsByEmailAndDeleteFlgFalse(String email);

	long countByRoleAndDeleteFlgFalse(Role role);

	List<UserLogin> findByRoleInAndDeleteFlgFalse(Collection<Role> roles);
}
