package com.ykskocluk.demo;

import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Conversation;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.ConversationRepository;
import com.ykskocluk.demo.repository.UniversityRepository;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The debounce claim, against real PostgreSQL — it is the only thing preventing a burst of chat
 * messages from becoming a burst of email, and it carries the same
 * "conditional UPDATE, rows-affected is the verdict" guarantee as the session-reminder claim.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class MessageNotificationClaimTest {

    private static final Duration DEBOUNCE = Duration.ofMinutes(30);

    @Autowired MessageService messageService;
    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired ConversationRepository conversationRepository;

    private long conversationId;

    @BeforeEach
    void setUp() {
        University uni = new University();
        uni.setName("Claim Uni " + System.nanoTime());
        universityRepository.save(uni);

        CoachProfile coach = new CoachProfile();
        coach.setUser(TestUsers.create(userRepository, Role.COACH));
        coach.setUniversity(uni);
        coach.setHeadline("Koç");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(5);
        coach.setActiveStudentCount(0);
        coachProfileRepository.save(coach);

        Conversation conversation = new Conversation();
        conversation.setStudent(TestUsers.create(userRepository, Role.STUDENT));
        conversation.setCoachProfile(coach);
        conversation.setLastMessageAt(Instant.now());
        conversationId = conversationRepository.save(conversation).getId();
    }

    @Test
    void firstClaimWins_secondInsideTheWindowIsRefused() {
        Instant now = Instant.now();

        assertThat(claimForStudent(now)).isTrue();
        assertThat(claimForStudent(now.plus(Duration.ofMinutes(5)))).isFalse();
        assertThat(claimForStudent(now.plus(Duration.ofMinutes(29)))).isFalse();
    }

    @Test
    void claimSucceedsAgainOnceTheWindowHasPassed() {
        Instant now = Instant.now();
        assertThat(claimForStudent(now)).isTrue();

        assertThat(claimForStudent(now.plus(Duration.ofMinutes(31)))).isTrue();
    }

    @Test
    void theTwoDirectionsDoNotSilenceEachOther() {
        Instant now = Instant.now();

        // A mail to the coach must leave the student's own window untouched — one shared column
        // would have made the second claim fail here.
        assertThat(claimForCoach(now)).isTrue();
        assertThat(claimForStudent(now)).isTrue();
    }

    @Test
    void concurrentClaimsForTheSameRecipient_onlyOneWins() throws Exception {
        Instant now = Instant.now();
        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            List<Callable<Boolean>> tasks = java.util.Collections.nCopies(attempts,
                    () -> claimForStudent(now));
            List<Future<Boolean>> results = pool.invokeAll(tasks);

            long won = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    won++;
                }
            }
            // Two messages landing at once must not produce two emails.
            assertThat(won).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean claimForStudent(Instant now) {
        return messageService.claimEmailNotification(conversationId, true, now, now.minus(DEBOUNCE));
    }

    private boolean claimForCoach(Instant now) {
        return messageService.claimEmailNotification(conversationId, false, now, now.minus(DEBOUNCE));
    }
}
