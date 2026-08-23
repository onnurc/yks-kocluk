package com.ykskocluk.demo.service;

import com.ykskocluk.demo.enums.*;
import com.ykskocluk.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {
    @Mock UserRepository users;
    @Mock CoachProfileRepository coaches;
    @Mock SubscriptionRepository subscriptions;
    @Mock PaymentRepository payments;
    @Mock ReportRepository reports;
    @Mock SessionRepository sessions;
    @Mock TrialConsultationRepository trials;
    @Mock CoachApplicationRepository coachApplications;
    AdminDashboardService service;

    @BeforeEach void setUp() {
        service = new AdminDashboardService(users, coaches, subscriptions, payments, reports, sessions, trials, coachApplications);
    }

    @Test
    void summaryUsesDomainStatusDefinitionsAndMonthlySuccessfulLedgerRows() {
        when(users.countByRoleAndStatusNot(Role.STUDENT, UserStatus.DELETED)).thenReturn(40L);
        when(users.countByRoleAndStatusNot(Role.COACH, UserStatus.DELETED)).thenReturn(10L);
        when(coaches.countOperational(CoachProfileStatus.APPROVED, UserStatus.ACTIVE)).thenReturn(8L);
        when(coachApplications.countByStatus(CoachApplicationStatus.PENDING)).thenReturn(3L);
        when(subscriptions.countByStatusIn(anyCollection())).thenReturn(12L);
        when(payments.countForPeriod(eq(PaymentType.CHARGE), eq(PaymentStatus.SUCCESS), any(), any())).thenReturn(5L);
        when(payments.sumForPeriod(eq(PaymentType.CHARGE), eq(PaymentStatus.SUCCESS), any(), any()))
                .thenReturn(new BigDecimal("10000.00"));
        when(payments.sumForPeriod(eq(PaymentType.REFUND), eq(PaymentStatus.SUCCESS), any(), any()))
                .thenReturn(new BigDecimal("750.00"));
        when(reports.countByStatusIn(anyCollection())).thenReturn(4L);
        when(sessions.countByStatusAndStartTimeAfter(eq(SessionStatus.PLANNED), any())).thenReturn(6L);
        when(trials.countByStatusInAndStartTimeAfter(anyCollection(), any())).thenReturn(2L);
        when(sessions.countByStatusAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                eq(SessionStatus.COMPLETED), any(), any())).thenReturn(9L);

        var result = service.summary();

        assertThat(result.totalStudentCount()).isEqualTo(40);
        assertThat(result.totalCoachCount()).isEqualTo(10);
        assertThat(result.activeCoachCount()).isEqualTo(8);
        assertThat(result.pendingCoachApplicationCount()).isEqualTo(3);
        assertThat(result.activeSubscriptionCount()).isEqualTo(12);
        assertThat(result.salesThisMonthCount()).isEqualTo(5);
        assertThat(result.grossRevenueThisMonth()).isEqualByComparingTo("10000.00");
        assertThat(result.refundAmountThisMonth()).isEqualByComparingTo("750.00");
        assertThat(result.netCollectedThisMonth()).isEqualByComparingTo("9250.00");
        assertThat(result.openReportCount()).isEqualTo(4);
        assertThat(result.scheduledSessionCount()).isEqualTo(8);
        assertThat(result.completedSessionCountThisMonth()).isEqualTo(9);
    }

    @Test
    void financeComputesCollectedNotProfit() {
        when(payments.sumForPeriod(PaymentType.CHARGE, PaymentStatus.SUCCESS, null, null))
                .thenReturn(new BigDecimal("5000.00"));
        when(payments.sumForPeriod(PaymentType.REFUND, PaymentStatus.SUCCESS, null, null))
                .thenReturn(new BigDecimal("1250.00"));

        var result = service.finance(null, null);

        assertThat(result.netCollectedAmount()).isEqualByComparingTo("3750.00");
    }
}
