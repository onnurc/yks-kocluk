package com.ykskocluk.demo;

import com.ykskocluk.demo.dto.SessionCreateRequest;
import com.ykskocluk.demo.entity.CoachAvailability;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.CoachAvailabilityRepository;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.PackageRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The weekly-quota boundary. Package = 1 session/week (Mon–Sun, Europe/Istanbul). The
 * last-allowed booking in a week succeeds; the next one in the SAME week is QUOTA_EXCEEDED;
 * a booking in the NEXT week succeeds — proving the window is per Istanbul week, not rolling.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SessionQuotaBoundaryTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    @Autowired SessionService sessionService;
    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired PackageRepository packageRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired CoachAvailabilityRepository availabilityRepository;

    @Test
    void weeklyQuota_lastAllowedSucceeds_nextSameWeekFails_nextWeekSucceeds() {
        University uni = new University();
        uni.setName("Quota University " + System.nanoTime());
        universityRepository.save(uni);

        CoachProfile coach = new CoachProfile();
        User coachUser = new User();
        coachUser.setEmail("coach-q-" + System.nanoTime() + "@example.com");
        coachUser.setFullName("Coach");
        coachUser.setRole(Role.COACH);
        coachUser.setStatus(UserStatus.ACTIVE);
        userRepository.save(coachUser);
        coach.setUser(coachUser);
        coach.setUniversity(uni);
        coach.setHeadline("Koç");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(5);
        coach.setActiveStudentCount(1);
        coachProfileRepository.save(coach);

        // Cheapest seeded package is 'Aylık 1x' — exactly 1 session/week.
        Package pkg = packageRepository.findByActiveTrueOrderByPriceAsc().get(0);
        assertThat(pkg.getWeeklySessions()).isEqualTo(1);

        User student = new User();
        student.setEmail("student-q-" + System.nanoTime() + "@example.com");
        student.setFullName("Student");
        student.setRole(Role.STUDENT);
        student.setStatus(UserStatus.ACTIVE);
        userRepository.save(student);

        Subscription sub = new Subscription();
        sub.setStudent(student);
        sub.setCoachProfile(coach);
        sub.setPkg(pkg);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartAt(Instant.now());
        sub.setEndAt(Instant.now().plus(java.time.Duration.ofDays(60)));
        subscriptionRepository.save(sub);

        // A Monday two weeks out (safely in the future), in Istanbul.
        LocalDate monday = LocalDate.now(ISTANBUL)
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(1);
        long slotWeekA1 = slot(coach, monday, DayOfWeek.TUESDAY);          // week A
        long slotWeekA2 = slot(coach, monday, DayOfWeek.WEDNESDAY);        // week A (same week)
        long slotWeekB = slot(coach, monday.plusWeeks(1), DayOfWeek.TUESDAY); // week B (next week)

        // 1st booking in week A -> OK (last allowed, limit is 1)
        assertThatNoException().isThrownBy(() ->
                sessionService.book(student.getId(), new SessionCreateRequest(slotWeekA1)));

        // 2nd booking in the SAME week -> QUOTA_EXCEEDED
        ApiException ex = catchThrowableOfType(ApiException.class, () ->
                sessionService.book(student.getId(), new SessionCreateRequest(slotWeekA2)));
        assertThat(ex.getErrorCode()).isEqualTo("QUOTA_EXCEEDED");

        // booking in the NEXT week -> OK (fresh quota window)
        assertThatNoException().isThrownBy(() ->
                sessionService.book(student.getId(), new SessionCreateRequest(slotWeekB)));
    }

    private long slot(CoachProfile coach, LocalDate monday, DayOfWeek day) {
        Instant start = monday.with(TemporalAdjusters.nextOrSame(day))
                .atTime(LocalTime.of(10, 0)).atZone(ISTANBUL).toInstant();
        CoachAvailability slot = new CoachAvailability();
        slot.setCoachProfile(coach);
        slot.setStartTime(start);
        slot.setEndTime(start.plus(java.time.Duration.ofHours(1)));
        slot.setBooked(false);
        return availabilityRepository.save(slot).getId();
    }
}
