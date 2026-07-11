package com.ykskocluk.demo;

import com.ykskocluk.demo.dto.SessionCreateRequest;
import com.ykskocluk.demo.entity.CoachAvailability;
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
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.CoachAvailabilityRepository;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.PackageRepository;
import com.ykskocluk.demo.repository.SessionRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UniversityRepository;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Phase 4d cancellation/lifecycle rules: the 24h boundary (just-over → CANCELLED + slot
 * reopened; just-under → LATE_CANCELLED + slot kept), quota return on early cancel vs. quota
 * burn on LATE_CANCELLED/NO_SHOW (proving those statuses joined the quota set), and the coach
 * COMPLETED/NO_SHOW transitions with their guards.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SessionLifecycleTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    @Autowired SessionService sessionService;
    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired PackageRepository packageRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired CoachAvailabilityRepository availabilityRepository;
    @Autowired SessionRepository sessionRepository;

    // --- 24h cancellation boundary ---

    @Test
    void cancel_justOver24h_isEarly_cancelled_andReopensSlot() {
        Fixture f = fixture();
        CoachAvailability slot = slot(f.coach, Instant.now().plus(Duration.ofHours(24).plusMinutes(10)));
        Session session = plannedSession(f, slot);

        sessionService.cancel(f.student.getId(), session.getId());

        Session reloaded = sessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(reloaded.getAvailability()).isNull();                       // slot unlinked
        assertThat(availabilityRepository.findById(slot.getId()).orElseThrow().isBooked()).isFalse(); // reopened
    }

    @Test
    void cancel_justUnder24h_isLate_lateCancelled_andKeepsSlot() {
        Fixture f = fixture();
        CoachAvailability slot = slot(f.coach, Instant.now().plus(Duration.ofHours(24).minusMinutes(10)));
        Session session = plannedSession(f, slot);

        sessionService.cancel(f.student.getId(), session.getId());

        Session reloaded = sessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SessionStatus.LATE_CANCELLED);
        assertThat(reloaded.getAvailability()).isNotNull();                    // slot stays linked
        assertThat(availabilityRepository.findById(slot.getId()).orElseThrow().isBooked()).isTrue(); // not reopened
    }

    @Test
    void cancel_notOwnerOrNotPlanned_isRejected() {
        Fixture f = fixture();
        CoachAvailability slot = slot(f.coach, Instant.now().plus(Duration.ofDays(3)));
        Session session = plannedSession(f, slot);

        // a different student cannot cancel it
        User other = student("other-" + System.nanoTime());
        ApiException notOwner = catchThrowableOfType(ApiException.class,
                () -> sessionService.cancel(other.getId(), session.getId()));
        assertThat(notOwner.getErrorCode()).isEqualTo("SESSION_NOT_FOUND");

        // already COMPLETED → cannot cancel
        session.setStatus(SessionStatus.COMPLETED);
        sessionRepository.save(session);
        ApiException badState = catchThrowableOfType(ApiException.class,
                () -> sessionService.cancel(f.student.getId(), session.getId()));
        assertThat(badState.getErrorCode()).isEqualTo("INVALID_SESSION_STATE");
    }

    // --- quota return vs burn ---

    @Test
    void earlyCancel_returnsQuota_allowingRebookSameWeek() {
        Fixture f = fixture();
        LocalDate monday = futureMonday();
        long slotA = slot(f.coach, at(monday, DayOfWeek.TUESDAY)).getId();
        long slotB = slot(f.coach, at(monday, DayOfWeek.WEDNESDAY)).getId();

        // book A (PLANNED, consumes the single weekly seat)
        var booked = sessionService.book(f.student.getId(), new SessionCreateRequest(slotA));
        // B in the same week is now blocked
        assertThat(catchThrowableOfType(ApiException.class,
                () -> sessionService.book(f.student.getId(), new SessionCreateRequest(slotB)))
                .getErrorCode()).isEqualTo("QUOTA_EXCEEDED");

        // early-cancel A → CANCELLED (not quota-consuming) → quota returned
        sessionService.cancel(f.student.getId(), booked.id());

        assertThatNoException().isThrownBy(() ->
                sessionService.book(f.student.getId(), new SessionCreateRequest(slotB)));
    }

    @Test
    void lateCancelledAndNoShowConsumeQuota_butCancelledDoesNot() {
        LocalDate monday = futureMonday();

        // LATE_CANCELLED in the week blocks a new booking in that week
        assertThat(rebookBlockedGivenPriorStatus(monday, SessionStatus.LATE_CANCELLED)).isTrue();
        // NO_SHOW in the week blocks too
        assertThat(rebookBlockedGivenPriorStatus(monday, SessionStatus.NO_SHOW)).isTrue();
        // CANCELLED does NOT consume — rebooking is allowed
        assertThat(rebookBlockedGivenPriorStatus(monday, SessionStatus.CANCELLED)).isFalse();
    }

    // --- coach transitions ---

    @Test
    void coach_complete_and_noShow_transitionPlannedSessions_withGuards() {
        Fixture f = fixture();
        Long coachUserId = f.coach.getUser().getId();

        Session toComplete = plannedSession(f, slot(f.coach, Instant.now().plus(Duration.ofDays(2))));
        sessionService.markCompleted(coachUserId, toComplete.getId());
        assertThat(sessionRepository.findById(toComplete.getId()).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.COMPLETED);

        Session toNoShow = plannedSession(f, slot(f.coach, Instant.now().plus(Duration.ofDays(2).plusHours(2))));
        sessionService.markNoShow(coachUserId, toNoShow.getId());
        assertThat(sessionRepository.findById(toNoShow.getId()).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.NO_SHOW);

        // a different coach cannot act on it
        CoachProfile otherCoach = fixture().coach;
        ApiException notOwner = catchThrowableOfType(ApiException.class,
                () -> sessionService.markCompleted(otherCoach.getUser().getId(), toComplete.getId()));
        assertThat(notOwner.getErrorCode()).isEqualTo("SESSION_NOT_FOUND");

        // already COMPLETED → cannot complete again
        ApiException badState = catchThrowableOfType(ApiException.class,
                () -> sessionService.markCompleted(coachUserId, toComplete.getId()));
        assertThat(badState.getErrorCode()).isEqualTo("INVALID_SESSION_STATE");
    }

    // --- helpers ---

    /** Creates a prior session with {@code priorStatus} in the week, then tries a new booking
     *  in the same week with a 1×/week package. Returns true if the new booking was blocked. */
    private boolean rebookBlockedGivenPriorStatus(LocalDate monday, SessionStatus priorStatus) {
        Fixture f = fixture();
        CoachAvailability priorSlot = slot(f.coach, at(monday, DayOfWeek.TUESDAY));
        Session prior = new Session();
        prior.setStudent(f.student);
        prior.setCoachProfile(f.coach);
        prior.setSubscription(f.subscription);
        prior.setAvailability(priorStatus == SessionStatus.CANCELLED ? null : priorSlot);
        prior.setStatus(priorStatus);
        prior.setStartTime(priorSlot.getStartTime());
        prior.setEndTime(priorSlot.getEndTime());
        sessionRepository.save(prior);

        long newSlot = slot(f.coach, at(monday, DayOfWeek.WEDNESDAY)).getId();
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> sessionService.book(f.student.getId(), new SessionCreateRequest(newSlot)));
        return ex != null && "QUOTA_EXCEEDED".equals(ex.getErrorCode());
    }

    private record Fixture(CoachProfile coach, User student, Subscription subscription) {
    }

    private Fixture fixture() {
        University uni = new University();
        uni.setName("Lifecycle Uni " + System.nanoTime());
        universityRepository.save(uni);

        CoachProfile coach = new CoachProfile();
        coach.setUser(user("coach-" + System.nanoTime() + "@example.com", Role.COACH));
        coach.setUniversity(uni);
        coach.setHeadline("Koç");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(5);
        coach.setActiveStudentCount(1);
        coachProfileRepository.save(coach);

        User student = student("student-" + System.nanoTime());
        Package pkg = packageRepository.findByActiveTrueOrderByPriceAsc().get(0); // 1×/week

        Subscription sub = new Subscription();
        sub.setStudent(student);
        sub.setCoachProfile(coach);
        sub.setPkg(pkg);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartAt(Instant.now());
        sub.setEndAt(Instant.now().plus(Duration.ofDays(90)));
        subscriptionRepository.save(sub);

        return new Fixture(coach, student, sub);
    }

    private User user(String email, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setFullName("User");
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.save(u);
    }

    private User student(String prefix) {
        return user(prefix + "@example.com", Role.STUDENT);
    }

    private CoachAvailability slot(CoachProfile coach, Instant start) {
        CoachAvailability slot = new CoachAvailability();
        slot.setCoachProfile(coach);
        slot.setStartTime(start);
        slot.setEndTime(start.plus(Duration.ofHours(1)));
        slot.setBooked(false);
        return availabilityRepository.save(slot);
    }

    private Session plannedSession(Fixture f, CoachAvailability slot) {
        slot.setBooked(true);
        availabilityRepository.save(slot);
        Session s = new Session();
        s.setStudent(f.student);
        s.setCoachProfile(f.coach);
        s.setSubscription(f.subscription);
        s.setAvailability(slot);
        s.setStatus(SessionStatus.PLANNED);
        s.setStartTime(slot.getStartTime());
        s.setEndTime(slot.getEndTime());
        return sessionRepository.save(s);
    }

    private LocalDate futureMonday() {
        return LocalDate.now(ISTANBUL).with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(1);
    }

    private Instant at(LocalDate monday, DayOfWeek day) {
        return monday.with(TemporalAdjusters.nextOrSame(day))
                .atTime(LocalTime.of(10, 0)).atZone(ISTANBUL).toInstant();
    }
}
