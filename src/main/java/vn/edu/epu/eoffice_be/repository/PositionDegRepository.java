package vn.edu.epu.eoffice_be.repository;

import vn.edu.epu.eoffice_be.entity.PositionDeg;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PositionDegRepository extends JpaRepository<PositionDeg, Long> {

    Optional<PositionDeg> findByDegCdAndDeleteFlgFalse(String degCd);

    List<PositionDeg> findByOrgNodeIdAndDeleteFlgFalse(Long orgNodeId);
}