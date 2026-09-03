package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.enums.PackageType;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CancellationCalculationServiceTest {
    private static final Instant START = Instant.parse("2026-01-10T10:00:00Z");
    private final CancellationCalculationService service =
            new CancellationCalculationService(mock(SubscriptionRepository.class));

    @Test void oneMonthIsNotRefundable() {
        var result = service.calculate(subscription(PackageType.ONE_MONTH, "3000", "3000", 1),
                START.plusSeconds(86400));
        assertThat(result.refundableAmount()).isEqualByComparingTo("0.00");
        assertThat(result.policy()).isEqualTo(CancellationCalculationService.ONE_MONTH_POLICY);
    }

    @Test void threeMonthsCancellationDuringMonthOneUsesOneRawMonth() {
        var result = service.calculate(subscription(PackageType.THREE_MONTHS, "8000", "3000", 3),
                START.plusSeconds(10 * 86400));
        assertThat(result.usedMonths()).isEqualTo(1);
        assertThat(result.refundableAmount()).isEqualByComparingTo("5000.00");
        assertThat(result.consumedAmount()).isEqualByComparingTo("3000.00");
        assertThat(result.accessEndsAt()).isEqualTo(START.atZone(java.time.ZoneId.of("Europe/Istanbul")).plusMonths(1).toInstant());
    }

    @Test void threeMonthsCancellationDuringMonthTwoUsesTwoRawMonths() {
        var result = service.calculate(subscription(PackageType.THREE_MONTHS, "8000", "3000", 3),
                START.atZone(java.time.ZoneId.of("Europe/Istanbul")).plusMonths(1).plusDays(2).toInstant());
        assertThat(result.usedMonths()).isEqualTo(2);
        assertThat(result.refundableAmount()).isEqualByComparingTo("2000.00");
    }

    @Test void refundFloorsAtZero() {
        var result = service.calculate(subscription(PackageType.THREE_MONTHS, "5000", "3000", 3),
                START.atZone(java.time.ZoneId.of("Europe/Istanbul")).plusMonths(2).toInstant());
        assertThat(result.refundableAmount()).isEqualByComparingTo("0.00");
        assertThat(result.consumedAmount()).isEqualByComparingTo("5000.00");
    }

    @Test void currentPackagePriceAndMeetingAttendanceCannotChangeSnapshotCalculation() {
        Subscription subscription = subscription(PackageType.THREE_MONTHS, "8000", "3000", 3);
        Package current = new Package();
        current.setPrice(new BigDecimal("99999"));
        current.setWeeklySessions(99);
        subscription.setPkg(current);
        var result = service.calculate(subscription, START.plusSeconds(86400));
        assertThat(result.refundableAmount()).isEqualByComparingTo("5000.00");
    }

    private Subscription subscription(PackageType type, String paid, String rawMonth, int months) {
        Subscription subscription = new Subscription();
        subscription.setPackageTypeSnapshot(type);
        subscription.setEffectivePriceSnapshot(new BigDecimal(paid));
        subscription.setOneMonthBasePriceSnapshot(new BigDecimal(rawMonth));
        subscription.setStartAt(START);
        subscription.setEndAt(START.atZone(java.time.ZoneId.of("Europe/Istanbul")).plusMonths(months).toInstant());
        if (type == PackageType.UNTIL_EXAM) subscription.setUntilExamMonthsRemainingSnapshot(months);
        return subscription;
    }
}
