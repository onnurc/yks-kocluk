package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.AccountDeletionRequest;
import com.ykskocluk.demo.enums.AccountDeletionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequest, Long> {
    Optional<AccountDeletionRequest> findByUserId(Long userId);
    boolean existsByIdentityEmailHashAndStatus(String identityEmailHash, AccountDeletionStatus status);
    boolean existsByOauthSubjectHashAndStatus(String oauthSubjectHash, AccountDeletionStatus status);
}
