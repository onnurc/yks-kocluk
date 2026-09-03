package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.PackageCampaignResponse;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.PackageCampaign;
import com.ykskocluk.demo.entity.PackagePriceTier;
import com.ykskocluk.demo.entity.PackageSettings;
import com.ykskocluk.demo.enums.DiscountType;
import com.ykskocluk.demo.enums.PackageType;
import com.ykskocluk.demo.repository.PackageCampaignRepository;
import com.ykskocluk.demo.repository.PackagePriceTierRepository;
import com.ykskocluk.demo.repository.PackageSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;

@Service
public class PackagePricingService {
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final PackagePriceTierRepository tierRepository;
    private final PackageCampaignRepository campaignRepository;
    private final PackageSettingsRepository settingsRepository;

    public PackagePricingService(PackagePriceTierRepository tierRepository,
                                 PackageCampaignRepository campaignRepository,
                                 PackageSettingsRepository settingsRepository) {
        this.tierRepository = tierRepository;
        this.campaignRepository = campaignRepository;
        this.settingsRepository = settingsRepository;
    }

    @Transactional(readOnly = true)
    public PriceResolution resolve(Package pkg, Instant now) {
        LocalDate today = now.atZone(ISTANBUL).toLocalDate();
        ExamContext exam = pkg.getPackageType() == PackageType.UNTIL_EXAM ? activeExam(today) : null;
        Integer monthsRemaining = exam == null ? null : monthsRemaining(today, exam.examDate());
        BigDecimal listPrice = pkg.getPrice();
        if (pkg.getPackageType() == PackageType.UNTIL_EXAM) {
            listPrice = monthsRemaining == null ? null : tierRepository
                    .findByPkgIdAndMonthsRemaining(pkg.getId(), monthsRemaining)
                    .map(PackagePriceTier::getPrice)
                    .orElse(null);
        }

        PackageCampaign campaign = campaignRepository.findByPkgId(pkg.getId()).orElse(null);
        boolean campaignActive = isActive(campaign, now);
        BigDecimal effectivePrice = listPrice;
        if (campaignActive && listPrice != null) {
            effectivePrice = applyDiscount(listPrice, campaign.getDiscountType(), campaign.getDiscountValue());
        }
        PackageCampaignResponse campaignResponse = campaign == null ? null : new PackageCampaignResponse(
                campaign.isEnabled(), campaignActive, campaign.getTitle(), campaign.getDescription(),
                campaign.getStartsAt(), campaign.getEndsAt(), campaign.getDiscountType(), campaign.getDiscountValue());
        return new PriceResolution(listPrice, effectivePrice, monthsRemaining,
                exam == null ? null : exam.examYear(), exam == null ? null : exam.examDate(),
                campaignActive, campaignResponse);
    }

    @Transactional(readOnly = true)
    public Integer applicableMonthsRemaining(LocalDate today) {
        ExamContext exam = activeExam(today);
        return exam == null ? null : monthsRemaining(today, exam.examDate());
    }

    /**
     * Returns the count of purchase-anchored calendar service months needed to reach the exam.
     * Whole months are measured with calendar arithmetic from {@code from}; any remaining partial
     * month counts as a full service month. No day/30 or floating-point approximation is used.
     * Example: Jan 31 -> Feb 28 is one month, while Jan 31 -> Mar 1 is two months.
     */
    public static Integer monthsRemaining(LocalDate from, LocalDate examDate) {
        if (examDate == null || from == null || !examDate.isAfter(from)) return null;
        Period period = Period.between(from, examDate);
        int months = period.getYears() * 12 + period.getMonths();
        if (examDate.isAfter(from.plusMonths(months))) months++;
        return Math.max(months, 1);
    }

    private ExamContext activeExam(LocalDate today) {
        PackageSettings settings = settingsRepository.findById(1L).orElse(null);
        if (settings == null || !settings.isActive() || settings.getYksExamDate() == null
                || settings.getYksExamYear() == null || !settings.getYksExamDate().isAfter(today)) {
            return null;
        }
        return new ExamContext(settings.getYksExamYear(), settings.getYksExamDate());
    }

    public static boolean isActive(PackageCampaign campaign, Instant now) {
        return campaign != null && campaign.isEnabled()
                && !now.isBefore(campaign.getStartsAt()) && now.isBefore(campaign.getEndsAt());
    }

    public static BigDecimal applyDiscount(BigDecimal listPrice, DiscountType type, BigDecimal value) {
        if (listPrice == null || type == null || value == null) return listPrice;
        BigDecimal discount = type == DiscountType.PERCENTAGE
                ? listPrice.multiply(value).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : value;
        return listPrice.subtract(discount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public record PriceResolution(BigDecimal listPrice, BigDecimal effectivePrice,
                                  Integer untilExamMonthsRemaining, Integer yksExamYear,
                                  LocalDate yksExamDate, boolean campaignActive,
                                  PackageCampaignResponse campaign) {}

    private record ExamContext(Integer examYear, LocalDate examDate) {}
}
