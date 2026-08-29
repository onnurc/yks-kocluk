package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByGoogleSub(String googleSub);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    boolean existsByEmail(String email);

    long countByRoleAndStatusNot(Role role, UserStatus status);

    @Query("""
            select u from User u
             where (:role is null or u.role = :role)
               and (:status is null or u.status = :status)
               and (:search is null or lower(u.fullName) like lower(concat('%', cast(:search as string), '%'))
                    or lower(u.email) like lower(concat('%', cast(:search as string), '%')))
            """)
    Page<User> searchAdmin(@Param("role") Role role, @Param("status") UserStatus status,
                           @Param("search") String search, Pageable pageable);

    Page<User> findByRole(Role role, Pageable pageable);

    Page<User> findByRoleAndStatus(Role role, UserStatus status, Pageable pageable);

    @Query("""
            select u from User u
             where u.role = :role
               and (lower(u.fullName) like lower(concat('%', :search, '%'))
                    or lower(u.email) like lower(concat('%', :search, '%')))
            """)
    Page<User> searchByRole(@Param("role") Role role, @Param("search") String search, Pageable pageable);

    @Query("""
            select u from User u
             where u.role = :role and u.status = :status
               and (lower(u.fullName) like lower(concat('%', :search, '%'))
                    or lower(u.email) like lower(concat('%', :search, '%')))
            """)
    Page<User> searchByRoleAndStatus(@Param("role") Role role, @Param("status") UserStatus status,
                                     @Param("search") String search, Pageable pageable);
}
