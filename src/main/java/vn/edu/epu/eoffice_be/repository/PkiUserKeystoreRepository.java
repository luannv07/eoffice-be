package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.epu.eoffice_be.entity.PkiUserKeystoreEntity;
import java.util.Optional;

public interface PkiUserKeystoreRepository extends JpaRepository<PkiUserKeystoreEntity, Long> {
    Optional<PkiUserKeystoreEntity> findByUserIdAndCaId(Long userId, Long caId);
}
