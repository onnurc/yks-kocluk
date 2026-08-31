package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.StudentDashboardResponse;
import com.ykskocluk.demo.entity.*;
import com.ykskocluk.demo.enums.*;
import com.ykskocluk.demo.repository.PaymentRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentDashboardServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    SubscriptionRepository subscriptionRepository;

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    UserMapper userMapper;

    @InjectMocks
    StudentDashboardService studentDashboardService;

    @Test
    void getDashboardData_activeSubscription_success() {
        User student = new User();
        student.setEmail("student.active.demo@example.com");
        student.setFullName("Demo Active Student");
        student.setRole(Role.STUDENT);
        student.setStatus(UserStatus.ACTIVE);
        student.setEmailVerified(true);
        student.setLegalOnboardingCompleted(true);

        User coachUser = new User();
        coachUser.setFullName("Demo Coach");

        CoachProfile coachProfile = new CoachProfile();
        coachProfile.setUser(coachUser);

        com.ykskocluk.demo.entity.Package pkg = new com.ykskocluk.demo.entity.Package();
        pkg.setName("Aylık 2x");

        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setCoachProfile(coachProfile);
        sub.setPkg(pkg);
        sub.setStartAt(Instant.now());
        sub.setEndAt(Instant.now().plusSeconds(3600));

        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setAmount(new BigDecimal("2500.00"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(userMapper.toResponse(student)).thenReturn(new com.ykskocluk.demo.dto.UserResponse(
                1L, student.getEmail(), student.getFullName(), student.getRole(), student.getStatus(),
                null, true, true, true, null));
        when(subscriptionRepository.findCurrentCoachRelationship(1L)).thenReturn(Optional.of(sub));
        when(paymentRepository.findFirstBySubscriptionIdOrderByCreatedAtDesc(sub.getId())).thenReturn(Optional.of(payment));

        StudentDashboardResponse response = studentDashboardService.getDashboardData(1L);

        assertThat(response).isNotNull();
        assertThat(response.user().email()).isEqualTo("student.active.demo@example.com");
        assertThat(response.user().emailVerified()).isTrue();
        assertThat(response.user().legalOnboardingCompleted()).isTrue();
        assertThat(response.user().hasLocalPassword()).isTrue();
        assertThat(response.subscription().status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(response.payment().status()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void getDashboardData_prefersTheLiveRelationshipOverNewerTerminalHistory() {
        User student = new User();
        student.setRole(Role.STUDENT);
        student.setStatus(UserStatus.ACTIVE);
        User coachUser = new User();
        coachUser.setFullName("Live Coach");
        CoachProfile coach = new CoachProfile();
        coach.setUser(coachUser);
        com.ykskocluk.demo.entity.Package pkg = new com.ykskocluk.demo.entity.Package();
        pkg.setName("Live Package");
        Subscription live = new Subscription();
        live.setStatus(SubscriptionStatus.PAST_DUE);
        live.setCoachProfile(coach);
        live.setPkg(pkg);

        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(userMapper.toResponse(student)).thenReturn(new com.ykskocluk.demo.dto.UserResponse(
                1L, "student@example.com", "Student", Role.STUDENT, UserStatus.ACTIVE,
                null, true, true, true, null));
        when(subscriptionRepository.findCurrentCoachRelationship(1L)).thenReturn(Optional.of(live));

        StudentDashboardResponse response = studentDashboardService.getDashboardData(1L);

        assertThat(response.subscription().status()).isEqualTo(SubscriptionStatus.PAST_DUE);
        org.mockito.Mockito.verify(subscriptionRepository, org.mockito.Mockito.never())
                .findFirstByStudentIdOrderByCreatedAtDesc(1L);
    }
}
