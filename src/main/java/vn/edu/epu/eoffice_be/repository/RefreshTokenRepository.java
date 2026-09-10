package vn.edu.epu.eoffice_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.epu.eoffice_be.entity.RefreshToken;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByRt(String rt);

	List<RefreshToken> findByUserIdAndRevokedFalse(Long userId);

	@Modifying
	@Query("update RefreshToken t set t.revoked = true where t.userId = :userId and t.revoked = false")
	int revokeAllActiveByUserId(@Param("userId") Long userId);
}
