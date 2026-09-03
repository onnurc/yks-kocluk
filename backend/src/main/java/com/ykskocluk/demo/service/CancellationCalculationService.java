package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.CancellationCalculationResponse;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.enums.PackageType;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Pure calculation boundary for package-specific cancellation policy. UNTIL_EXAM has its own
 * policy identifier so future rules can be introduced without changing checkout snapshots.
 */
@Service
public class CancellationCalculationService {
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    public static final String ONE_MONTH_POLICY = "ONE_MONTH_NON_REFUNDABLE";
    public static final String THREE_MONTH_POLICY = "THREE_MONTHS_RAW_ONE_MONTH";
    public static final String UNTIL_EXAM_POLICY = "UNTIL_EXAM_RAW_ONE_MONTH_FOUNDATION";
    public static final String LEGACY_POLICY = "LEGACY_EXISTING_TERMS";

    private final SubscriptionRepository subscriptionRepository;

    public CancellationCalculationService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional(readOnly = true)
    public CancellationCalculationResponse calculateForStudent(Long subscriptionId, Long studentId, Instant requestedAt) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUBSCRIPTION_NOT_FOUND", "Abonelik bulunamadı"));
        if (!subscription.getStudent().getId().equals(studentId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_SUBSCRIPTION_OWNER", "Bu abonelik size ait değil");
        }
        return calculate(subscription, requestedAt);
    }

    public CancellationCalculationResponse calculate(Subscription subscription, Instant requestedAt) {
        PackageType type = subscription.getPackageTypeSnapshot();
        BigDecimal paid = zeroIfNull(subscription.getEffectivePriceSnapshot());
        if (type == null) {
            return new CancellationCalculationResponse(requestedAt, 0, subscription.getEndAt(), subscription.getEndAt(),
                    BigDecimal.ZERO.setScale(2), paid, LEGACY_POLICY, null);
        }
        if (type == PackageType.ONE_MONTH) {
            return new CancellationCalculationResponse(requestedAt, 1, subscription.getEndAt(), subscription.getEndAt(),
                    BigDecimal.ZERO.setScale(2), paid, ONE_MONTH_POLICY, type);
        }

        int maximumMonths = type == PackageType.THREE_MONTHS ? 3
                : Math.max(1, subscription.getUntilExamMonthsRemainingSnapshot() == null
                    ? 1 : subscription.getUntilExamMonthsRemainingSnapshot());
        int usedMonths = usedMonths(subscription.getStartAt(), requestedAt, maximumMonths);
        Instant periodEnd = subscription.getStartAt().atZone(ISTANBUL).plusMonths(usedMonths).toInstant();
        Instant accessEndsAt = periodEnd.isBefore(subscription.getEndAt()) ? periodEnd : subscription.getEndAt();
        BigDecimal rawMonthly = zeroIfNull(subscription.getOneMonthBasePriceSnapshot());
        BigDecimal nominalConsumed = rawMonthly.multiply(BigDecimal.valueOf(usedMonths));
        BigDecimal refundable = paid.subtract(nominalConsumed).max(BigDecimal.ZERO).setScale(2);
        BigDecimal consumed = paid.subtract(refundable).max(BigDecimal.ZERO).setScale(2);
        return new CancellationCalculationResponse(requestedAt, usedMonths, accessEndsAt, accessEndsAt, refundable, consumed,
                type == PackageType.THREE_MONTHS ? THREE_MONTH_POLICY : UNTIL_EXAM_POLICY, type);
    }

    static int usedMonths(Instant startAt, Instant requestedAt, int maximumMonths) {
        if (startAt == null || requestedAt == null) return 1;
        int used = 1;
        while (used < maximumMonths && !requestedAt.isBefore(startAt.atZone(ISTANBUL).plusMonths(used).toInstant())) {
            used++;
        }
        return used;
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2);
    }
}
