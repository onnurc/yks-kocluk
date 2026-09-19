package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.TestcontainersConfiguration;
import com.ykskocluk.demo.config.JpaAuditingConfig;
import com.ykskocluk.demo.dto.SessionReminderView;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.Session;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.SessionStatus;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reminder-candidate query against real PostgreSQL. The {@code requireMeetLink} parameter mirrors
 * {@code app.meet-link.enabled}: while automatic generation is off nothing ever carries a link, so
 * a query that still demanded one would silently suppress every reminder.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class SessionRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired PackageRepository packageRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired SessionRepository sessionRepository;

    private User persistStudent() {
        User u = new User();
        u.setEmail("stu-" + System.nanoTime() + "@example.com");
        u.setFullName("Student");
        u.setRole(Role.STUDENT);
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.save(u);
    }

    private CoachProfile persistCoach() {
        User coachUser = new User();
        coachUser.setEmail("coach-" + System.nanoTime() + "@example.com");
        coachUser.setFullName("Ayşe Koç");
        coachUser.setRole(Role.COACH);
        coachUser.setStatus(UserStatus.ACTIVE);
        userRepository.save(coachUser);
        University uni = new University();
        uni.setName("Uni " + System.nanoTime());
        universityRepository.save(uni);
        CoachProfile coach = new CoachProfile();
        coach.setUser(coachUser);
        coach.setUniversity(uni);
        coach.setHeadline("Koç");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(10);
        coach.setActiveStudentCount(0);
        return coachProfileRepository.save(coach);
    }

    private Subscription persistSubscription(User student, CoachProfile coach) {
        Package pkg = new Package();
        pkg.setName("Pkg " + System.nanoTime());
        pkg.setWeeklySessions(1);
        pkg.setDurationDays(30);
        pkg.setPrice(new BigDecimal("1500.00"));
        pkg.setActive(true);
        packageRepository.save(pkg);

        Subscription s = new Subscription();
        s.setStudent(student);
        s.setCoachProfile(coach);
        s.setPkg(pkg);
        s.setStatus(SubscriptionStatus.ACTIVE);
        s.setStartAt(Instant.now());
        s.setEndAt(Instant.now().plus(30, ChronoUnit.DAYS));
        return subscriptionRepository.save(s);
    }

    /** A PLANNED session inside any reasonable reminder window, with or without a meet link. */
    private Session persistPlannedSession(String meetLink) {
        User student = persistStudent();
        CoachProfile coach = persistCoach();
        Session session = new Session();
        session.setStudent(student);
        session.setCoachProfile(coach);
        session.setSubscription(persistSubscription(student, coach));
        session.setStatus(SessionStatus.PLANNED);
        session.setStartTime(Instant.now().plus(6, ChronoUnit.HOURS));
        session.setEndTime(Instant.now().plus(7, ChronoUnit.HOURS));
        session.setMeetLink(meetLink);
        return sessionRepository.save(session);
    }

    private List<SessionReminderView> candidates(boolean requireMeetLink) {
        Instant now = Instant.now();
        return sessionRepository.findReminderCandidates(now, now.plus(24, ChronoUnit.HOURS), requireMeetLink);
    }

    @Test
    void requireMeetLinkFalse_includesSessionsWithoutALink() {
        Session withoutLink = persistPlannedSession(null);

        assertThat(candidates(false))
                .extracting(SessionReminderView::sessionId)
                .contains(withoutLink.getId());
    }

    @Test
    void requireMeetLinkTrue_excludesSessionsWithoutALink() {
        Session withoutLink = persistPlannedSession(null);

        assertThat(candidates(true))
                .extracting(SessionReminderView::sessionId)
                .doesNotContain(withoutLink.getId());
    }

    @Test
    void sessionWithALink_isACandidateEitherWay() {
        Session withLink = persistPlannedSession("https://meet.google.com/abc-defg-hij");

        assertThat(candidates(true)).extracting(SessionReminderView::sessionId).contains(withLink.getId());
        assertThat(candidates(false)).extracting(SessionReminderView::sessionId).contains(withLink.getId());
    }

    @Test
    void alreadyRemindedSession_isNeverACandidate() {
        Session session = persistPlannedSession(null);
        sessionRepository.claimReminder(session.getId(), Instant.now());

        assertThat(candidates(false))
                .extracting(SessionReminderView::sessionId)
                .doesNotContain(session.getId());
    }
}
