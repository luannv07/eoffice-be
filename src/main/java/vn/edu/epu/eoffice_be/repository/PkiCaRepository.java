package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.edu.epu.eoffice_be.entity.PkiCaEntity;

import java.util.Optional;

public interface PkiCaRepository extends JpaRepository<PkiCaEntity, Long> {
    @Query(value = "SELECT COUNT(*) FROM pki_user_keystores", nativeQuery = true)
    long countUserKeystores();
    Optional<PkiCaEntity> findByStatus(PkiCaEntity.Status status);

    default Optional<PkiCaEntity> findActive() {
        return findByStatus(PkiCaEntity.Status.ACTIVE);
    }
}
