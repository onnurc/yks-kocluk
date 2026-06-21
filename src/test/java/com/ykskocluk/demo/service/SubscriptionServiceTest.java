package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.SubscriptionCreateRequest;
import com.ykskocluk.demo.dto.SubscriptionResponse;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.SubscriptionMapper;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.PackageRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock PackageRepository packageRepository;
    @Mock CoachProfileRepository coachProfileRepository;
    @Mock UserRepository userRepository;
    @Mock SubscriptionMapper subscriptionMapper;

    SubscriptionService service;

    private static final long COACH_ID = 7L;
    private static final long PKG_ID = 3L;
    private static final long STUDENT_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(subscriptionRepository, packageRepository,
                coachProfileRepository, userRepository, subscriptionMapper);

        Package pkg = new Package();
        ReflectionTestUtils.setField(pkg, "id", PKG_ID);
        pkg.setActive(true);
        pkg.setDurationDays(30);
        pkg.setWeeklySessions(1);
        pkg.setPrice(BigDecimal.TEN);
        lenient().when(packageRepository.findById(PKG_ID)).thenReturn(Optional.of(pkg));

        CoachProfile coach = new CoachProfile();
        ReflectionTestUtils.setField(coach, "id", COACH_ID);
        coach.setStatus(CoachProfileStatus.APPROVED);
        lenient().when(coachProfileRepository.findByIdAndStatus(COACH_ID, CoachProfileStatus.APPROVED))
                .thenReturn(Optional.of(coach));

        lenient().when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(new User()));
        lenient().when(subscriptionMapper.toResponse(any())).thenReturn(
                new SubscriptionResponse(1L, COACH_ID, "Coach", PKG_ID, "Aylık 1x", 1,
                        SubscriptionStatus.ACTIVE, Instant.now(), Instant.now()));
    }

    private SubscriptionCreateRequest request() {
        return new SubscriptionCreateRequest(COACH_ID, PKG_ID);
    }

    @Test
    void subscribe_success_incrementsCapacityAndCreatesActive() {
        when(subscriptionRepository.existsByStudentIdAndCoachProfileIdAndStatus(
                STUDENT_ID, COACH_ID, SubscriptionStatus.ACTIVE)).thenReturn(false);
        when(coachProfileRepository.incrementActiveStudentCountIfRoom(COACH_ID)).thenReturn(1);

        service.subscribe(STUDENT_ID, request());

        verify(coachProfileRepository).incrementActiveStudentCountIfRoom(COACH_ID);
        verify(subscriptionRepository).saveAndFlush(any(Subscription.class));
    }

    @Test
    void subscribe_coachFull_throwsConflict_andDoesNotSave() {
        when(subscriptionRepository.existsByStudentIdAndCoachProfileIdAndStatus(
                STUDENT_ID, COACH_ID, SubscriptionStatus.ACTIVE)).thenReturn(false);
        when(coachProfileRepository.incrementActiveStudentCountIfRoom(COACH_ID)).thenReturn(0);

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.subscribe(STUDENT_ID, request()));
        assertThat(ex.getErrorCode()).isEqualTo("COACH_FULL");
        verify(subscriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void subscribe_alreadyActive_throwsConflict_noCapacityChange() {
        when(subscriptionRepository.existsByStudentIdAndCoachProfileIdAndStatus(
                STUDENT_ID, COACH_ID, SubscriptionStatus.ACTIVE)).thenReturn(true);

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.subscribe(STUDENT_ID, request()));
        assertThat(ex.getErrorCode()).isEqualTo("ALREADY_SUBSCRIBED");
        verify(coachProfileRepository, never()).incrementActiveStudentCountIfRoom(eq(COACH_ID));
    }

    @Test
    void subscribe_inactivePackage_throwsNotFound() {
        Package inactive = new Package();
        inactive.setActive(false);
        when(packageRepository.findById(PKG_ID)).thenReturn(Optional.of(inactive));

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.subscribe(STUDENT_ID, request()));
        assertThat(ex.getErrorCode()).isEqualTo("PACKAGE_NOT_FOUND");
    }

    @Test
    void subscribe_nonApprovedCoach_throwsNotFound() {
        when(coachProfileRepository.findByIdAndStatus(COACH_ID, CoachProfileStatus.APPROVED))
                .thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.subscribe(STUDENT_ID, request()));
        assertThat(ex.getErrorCode()).isEqualTo("COACH_NOT_FOUND");
    }
}
