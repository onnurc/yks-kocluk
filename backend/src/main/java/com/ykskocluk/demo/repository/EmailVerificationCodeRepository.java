package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.EmailVerificationCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EmailVerificationCode c join fetch c.user where c.user.id = :userId order by c.createdAt desc limit 1")
    Optional<EmailVerificationCode> findLatestForUpdate(@Param("userId") Long userId);

    Optional<EmailVerificationCode> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("update EmailVerificationCode c set c.usedAt = :now where c.user.id = :userId and c.usedAt is null")
    int invalidateAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from EmailVerificationCode c where c.expiresAt < :expiredBefore or (c.usedAt is not null and c.usedAt < :usedBefore)")
    int deleteRetired(@Param("expiredBefore") Instant expiredBefore, @Param("usedBefore") Instant usedBefore);
}
