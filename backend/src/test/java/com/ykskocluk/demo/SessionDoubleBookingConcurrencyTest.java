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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The double-booking race: two students booking the SAME availability slot at the same
 * instant. The slot row lock serializes the authoritative booked-state transition; the
 * UNIQUE(sessions.availability_id) constraint remains the database-level second guard. Exactly
 * one request succeeds and the other gets SLOT_TAKEN, so the slot can never back two sessions.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SessionDoubleBookingConcurrencyTest {

    @Autowired SessionService sessionService;
    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired PackageRepository packageRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired CoachAvailabilityRepository availabilityRepository;
    @Autowired SessionRepository sessionRepository;

    private User user(String email, Role role) {
        return TestUsers.create(userRepository, role, email);
    }

    private Subscription activeSub(User student, CoachProfile coach, Package pkg) {
        Subscription s = new Subscription();
        s.setStudent(student);
        s.setCoachProfile(coach);
        s.setPkg(pkg);
        s.setStatus(SubscriptionStatus.ACTIVE);
        s.setStartAt(Instant.now());
        s.setEndAt(Instant.now().plus(30, ChronoUnit.DAYS));
        return subscriptionRepository.save(s);
    }

    @Test
    void twoStudentsOneSlot_exactlyOneSucceeds() throws Exception {
        University uni = new University();
        uni.setName("DoubleBook University " + System.nanoTime());
        universityRepository.save(uni);

        CoachProfile coach = new CoachProfile();
        coach.setUser(user("coach-db-" + System.nanoTime() + "@example.com", Role.COACH));
        coach.setUniversity(uni);
        coach.setHeadline("Koç");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(5);
        coach.setActiveStudentCount(2);
        coachProfileRepository.save(coach);

        Package pkg = packageRepository.findByActiveTrueOrderByPriceAsc().get(0);

        User studentA = user("a-db-" + System.nanoTime() + "@example.com", Role.STUDENT);
        User studentB = user("b-db-" + System.nanoTime() + "@example.com", Role.STUDENT);
        activeSub(studentA, coach, pkg);
        activeSub(studentB, coach, pkg);

        CoachAvailability slot = new CoachAvailability();
        slot.setCoachProfile(coach);
        slot.setStartTime(Instant.now().plus(3, ChronoUnit.DAYS));
        slot.setEndTime(Instant.now().plus(3, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));
        slot.setBooked(false);
        availabilityRepository.save(slot);

        Long slotId = slot.getId();
        Long idA = studentA.getId();
        Long idB = studentB.getId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<String> fa = pool.submit(attempt(idA, slotId, start));
        Future<String> fb = pool.submit(attempt(idB, slotId, start));
        start.countDown(); // fire both at once

        String ra = fa.get(15, TimeUnit.SECONDS);
        String rb = fb.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(List.of(ra, rb)).containsExactlyInAnyOrder("OK", "SLOT_TAKEN");

        long sessionCount = sessionRepository.findByStudentIdOrderByStartTimeDesc(idA).size()
                + sessionRepository.findByStudentIdOrderByStartTimeDesc(idB).size();
        assertThat(sessionCount).isEqualTo(1); // slot never backed two sessions

        assertThat(availabilityRepository.findById(slotId).orElseThrow().isBooked()).isTrue();
    }

    private Callable<String> attempt(Long studentId, Long slotId, CountDownLatch start) {
        return () -> {
            start.await();
            try {
                sessionService.book(studentId, new SessionCreateRequest(slotId));
                return "OK";
            } catch (ApiException e) {
                return e.getErrorCode();
            }
        };
    }
}
