package com.ykskocluk.demo;

import com.ykskocluk.demo.dto.SubscriptionCreateRequest;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.PackageRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UniversityRepository;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The capacity race: a coach with exactly ONE seat, two students subscribing
 * simultaneously. The atomic conditional UPDATE must yield exactly one success and
 * one COACH_FULL — never two seats consumed.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SubscriptionCapacityConcurrencyTest {

    @Autowired SubscriptionService subscriptionService;
    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired PackageRepository packageRepository;
    @Autowired SubscriptionRepository subscriptionRepository;

    private User student(String email) {
        User u = new User();
        u.setEmail(email);
        u.setFullName("Student");
        u.setRole(Role.STUDENT);
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.save(u);
    }

    @Test
    void twoStudentsOneSeat_exactlyOneSucceeds() throws Exception {
        University uni = new University();
        uni.setName("Concurrency University " + System.nanoTime());
        universityRepository.save(uni);

        User coachUser = new User();
        coachUser.setEmail("coach-cap-" + System.nanoTime() + "@example.com");
        coachUser.setFullName("Coach");
        coachUser.setRole(Role.COACH);
        coachUser.setStatus(UserStatus.ACTIVE);
        userRepository.save(coachUser);

        CoachProfile coach = new CoachProfile();
        coach.setUser(coachUser);
        coach.setUniversity(uni);
        coach.setHeadline("Koç");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(1); // exactly one seat
        coach.setActiveStudentCount(0);
        coachProfileRepository.save(coach);

        Long coachId = coach.getId();
        Long studentA = student("a-cap-" + System.nanoTime() + "@example.com").getId();
        Long studentB = student("b-cap-" + System.nanoTime() + "@example.com").getId();
        Long packageId = packageRepository.findByActiveTrueOrderByPriceAsc().get(0).getId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> a = attempt(studentA, coachId, packageId, start);
        Callable<String> b = attempt(studentB, coachId, packageId, start);

        Future<String> fa = pool.submit(a);
        Future<String> fb = pool.submit(b);
        start.countDown(); // fire both at once

        String ra = fa.get(15, TimeUnit.SECONDS);
        String rb = fb.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(List.of(ra, rb)).containsExactlyInAnyOrder("OK", "COACH_FULL");

        CoachProfile reloaded = coachProfileRepository.findById(coachId).orElseThrow();
        assertThat(reloaded.getActiveStudentCount()).isEqualTo(1); // never over-incremented
        assertThat(subscriptionRepository.findByStudentIdOrderByCreatedAtDesc(studentA).size()
                + subscriptionRepository.findByStudentIdOrderByCreatedAtDesc(studentB).size()).isEqualTo(1);
    }

    private Callable<String> attempt(Long studentId, Long coachId, Long packageId, CountDownLatch start) {
        return () -> {
            start.await();
            try {
                subscriptionService.subscribe(studentId, new SubscriptionCreateRequest(coachId, packageId));
                return "OK";
            } catch (ApiException e) {
                return e.getErrorCode();
            }
        };
    }
}
