package vn.edu.epu.eoffice_be.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.epu.eoffice_be.entity.PasswordResetToken;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findByTokenHashAndDeleteFlgFalse(String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :usedAt where t.userLogin.id = :userId and t.usedAt is null and t.deleteFlg = false")
    int markUnusedTokensUsedByUserId(@Param("userId") Long userId, @Param("usedAt") Instant usedAt);
}
