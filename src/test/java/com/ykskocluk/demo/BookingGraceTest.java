package com.ykskocluk.demo;

import com.ykskocluk.demo.dto.SessionCreateRequest;
import com.ykskocluk.demo.dto.SessionResponse;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Phase 8 booking-gate widening: access stays open during the PAST_DUE grace window, but an
 * EXPIRED subscription cuts booking off. Proves {@code SessionService.findLiveSubscription} accepts
 * {ACTIVE, PAST_DUE} and rejects EXPIRED with NO_ACTIVE_SUBSCRIPTION.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class BookingGraceTest {

    @Autowired SessionService sessionService;
    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired PackageRepository packageRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired CoachAvailabilityRepository availabilityRepository;

    private User user(Role role) {
        User u = new User();
        u.setEmail(role.name().toLowerCase() + "-" + System.nanoTime() + "@example.com");
        u.setFullName("User");
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.save(u);
    }

    private CoachProfile coach() {
        University uni = new University();
        uni.setName("Grace University " + System.nanoTime());
        universityRepository.save(uni);
        CoachProfile c = new CoachProfile();
        c.setUser(user(Role.COACH));
        c.setUniversity(uni);
        c.setHeadline("Koç");
        c.setStatus(CoachProfileStatus.APPROVED);
        c.setMaxStudentCapacity(10);
        c.setActiveStudentCount(1);
        return coachProfileRepository.save(c);
    }

    private Long slot(CoachProfile coach) {
        CoachAvailability s = new CoachAvailability();
        s.setCoachProfile(coach);
        s.setStartTime(Instant.now().plus(3, ChronoUnit.DAYS));
        s.setEndTime(Instant.now().plus(3, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));
        s.setBooked(false);
        return availabilityRepository.save(s).getId();
    }

    private User studentWithSub(CoachProfile coach, SubscriptionStatus status) {
        User student = user(Role.STUDENT);
        Package pkg = packageRepository.findByActiveTrueOrderByPriceAsc().get(0);
        Subscription sub = new Subscription();
        sub.setStudent(student);
        sub.setCoachProfile(coach);
        sub.setPkg(pkg);
        sub.setStatus(status);
        sub.setStartAt(Instant.now().minus(10, ChronoUnit.DAYS));
        sub.setEndAt(Instant.now().plus(20, ChronoUnit.DAYS));
        subscriptionRepository.save(sub);
        return student;
    }

    @Test
    void pastDueStudent_canStillBook() {
        CoachProfile coach = coach();
        User student = studentWithSub(coach, SubscriptionStatus.PAST_DUE);
        Long slotId = slot(coach);

        SessionResponse session = sessionService.book(student.getId(), new SessionCreateRequest(slotId));

        assertThat(session).isNotNull();
        assertThat(session.id()).isNotNull();
    }

    @Test
    void expiredStudent_isBlocked() {
        CoachProfile coach = coach();
        User student = studentWithSub(coach, SubscriptionStatus.EXPIRED);
        Long slotId = slot(coach);

        assertThatThrownBy(() -> sessionService.book(student.getId(), new SessionCreateRequest(slotId)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo("NO_ACTIVE_SUBSCRIPTION"));
    }
}
