package com.ykskocluk.demo;

import com.ykskocluk.demo.dto.TrialConsultationCreateRequest;
import com.ykskocluk.demo.entity.CoachAvailability;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.AvailabilityPurpose;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.CoachAvailabilityRepository;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.TrialConsultationRepository;
import com.ykskocluk.demo.repository.UniversityRepository;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.service.CoachAvailabilityService;
import com.ykskocluk.demo.service.TrialConsultationService;
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

/** Verifies the authoritative trial-slot lock and the post-booking availability response. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class TrialConsultationDoubleBookingConcurrencyTest {

    @Autowired TrialConsultationService trialService;
    @Autowired CoachAvailabilityService availabilityService;
    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired CoachAvailabilityRepository availabilityRepository;
    @Autowired TrialConsultationRepository trialRepository;

    @Test
    void twoStudentsRequestingOneTrialSlot_exactlyOneSucceedsAndSlotLeavesAvailability() throws Exception {
        University university = new University();
        university.setName("Trial Concurrency University " + System.nanoTime());
        universityRepository.save(university);

        CoachProfile coach = new CoachProfile();
        coach.setUser(TestUsers.create(userRepository, Role.COACH));
        coach.setUniversity(university);
        coach.setHeadline("Koç");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(5);
        coachProfileRepository.save(coach);

        User studentA = TestUsers.create(userRepository, Role.STUDENT);
        User studentB = TestUsers.create(userRepository, Role.STUDENT);

        CoachAvailability slot = new CoachAvailability();
        slot.setCoachProfile(coach);
        slot.setStartTime(Instant.now().plus(3, ChronoUnit.DAYS));
        slot.setEndTime(slot.getStartTime().plus(30, ChronoUnit.MINUTES));
        slot.setPurpose(AvailabilityPurpose.TRIAL);
        slot.setBooked(false);
        availabilityRepository.saveAndFlush(slot);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<String> resultA = pool.submit(attempt(studentA.getId(), slot.getId(), start));
        Future<String> resultB = pool.submit(attempt(studentB.getId(), slot.getId(), start));
        start.countDown();

        String first = resultA.get(15, TimeUnit.SECONDS);
        String second = resultB.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(List.of(first, second)).containsExactlyInAnyOrder("OK", "SLOT_TAKEN");
        assertThat(availabilityRepository.findById(slot.getId()).orElseThrow().isBooked()).isTrue();
        assertThat(trialRepository.findByCoachProfileIdOrderByStartTimeDesc(coach.getId())).hasSize(1);
        assertThat(availabilityService.listOpenTrialSlots(coach.getId()))
                .noneMatch(available -> available.id().equals(slot.getId()));
    }

    private Callable<String> attempt(Long studentId, Long availabilityId, CountDownLatch start) {
        return () -> {
            start.await();
            try {
                trialService.request(studentId, new TrialConsultationCreateRequest(availabilityId));
                return "OK";
            } catch (ApiException error) {
                return error.getErrorCode();
            }
        };
    }
}
