package vn.edu.epu.eoffice_be.repository;

import vn.edu.epu.eoffice_be.entity.UserLogin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserLoginRepository extends JpaRepository<UserLogin, Long> {

    Optional<UserLogin> findByUsernameAndDeleteFlgFalse(String username);

    Optional<UserLogin> findByIdAndDeleteFlgFalse(Long id);

    boolean existsByUsernameAndDeleteFlgFalse(String username);

    boolean existsByEmailAndDeleteFlgFalse(String email);
}