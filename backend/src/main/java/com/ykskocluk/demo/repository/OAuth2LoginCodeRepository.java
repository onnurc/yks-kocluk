package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.OAuth2LoginCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OAuth2LoginCodeRepository extends JpaRepository<OAuth2LoginCode, Long> {

    Optional<OAuth2LoginCode> findByCodeHash(String codeHash);

    @Modifying
    @Query("UPDATE OAuth2LoginCode c SET c.consumedAt = :now WHERE c.codeHash = :codeHash AND c.consumedAt IS NULL AND c.expiresAt > :now")
    int consumeCodeAtomically(@Param("codeHash") String codeHash, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE OAuth2LoginCode c SET c.consumedAt = :now WHERE c.user.id = :userId AND c.consumedAt IS NULL")
    int consumeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
