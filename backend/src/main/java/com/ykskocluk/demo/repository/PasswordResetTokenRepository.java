package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t join fetch t.user where t.tokenHash = :hash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("hash") String hash);

    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.user.id = :userId and t.usedAt is null")
    int invalidateAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :expiredBefore or (t.usedAt is not null and t.usedAt < :usedBefore)")
    int deleteRetired(@Param("expiredBefore") Instant expiredBefore, @Param("usedBefore") Instant usedBefore);

    @Modifying
    @Query("delete from PasswordResetToken t where t.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
