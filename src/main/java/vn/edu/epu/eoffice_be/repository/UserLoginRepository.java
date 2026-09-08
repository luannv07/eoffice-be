package vn.edu.epu.eoffice_be.repository;

import jakarta.persistence.LockModeType;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface UserLoginRepository extends JpaRepository<UserLogin, Long>, JpaSpecificationExecutor<UserLogin> {

    Optional<UserLogin> findByUsernameAndDeleteFlgFalse(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserLogin> findByEmailAndDeleteFlgFalse(String email);

    Optional<UserLogin> findByIdAndDeleteFlgFalse(Long id);

    boolean existsByUsernameAndDeleteFlgFalse(String username);

    boolean existsByEmailAndDeleteFlgFalse(String email);

    long countByRoleAndDeleteFlgFalse(Role role, boolean deleteFlg);
}
