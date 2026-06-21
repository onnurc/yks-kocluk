package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.TestcontainersConfiguration;
import com.ykskocluk.demo.config.JpaAuditingConfig;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository slice against real PostgreSQL (Testcontainers). Imports JpaAuditingConfig
 * so @CreatedDate/@LastModifiedDate populate (the slice doesn't enable auditing by default).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    UserRepository userRepository;

    private User newUser(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("hashed");
        u.setFullName("Test User");
        u.setRole(Role.STUDENT);
        u.setStatus(UserStatus.ACTIVE);
        u.setEmailVerified(false);
        return u;
    }

    @Test
    void findByEmailAndExistsByEmail() {
        userRepository.save(newUser("found@example.com"));

        assertThat(userRepository.findByEmail("found@example.com")).isPresent();
        assertThat(userRepository.existsByEmail("found@example.com")).isTrue();
        assertThat(userRepository.findByEmail("missing@example.com")).isEmpty();
    }

    @Test
    void auditColumnsArePopulatedOnSave() {
        User saved = userRepository.save(newUser("audit@example.com"));
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void duplicateEmailViolatesUniqueConstraint() {
        userRepository.saveAndFlush(newUser("dup@example.com"));
        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("dup@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
