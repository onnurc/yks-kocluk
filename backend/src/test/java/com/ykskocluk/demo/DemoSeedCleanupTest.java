package com.ykskocluk.demo;

import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DemoSeedCleanupTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void whenDemoSeedIsDisabled_demoUsersAreCleanedUp() {
        List<String> demoEmails = List.of(
            "admin.demo@example.com",
            "student.demo@example.com",
            "coach.demo@example.com",
            "suspended.demo@example.com",
            "student.pending.demo@example.com",
            "student.active.demo@example.com"
        );

        for (String email : demoEmails) {
            Optional<User> user = userRepository.findByEmail(email);
            assertThat(user).isEmpty();
        }
    }
}
