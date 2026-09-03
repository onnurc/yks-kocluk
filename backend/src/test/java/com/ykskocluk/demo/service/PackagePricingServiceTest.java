package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.PackageCampaign;
import com.ykskocluk.demo.entity.PackagePriceTier;
import com.ykskocluk.demo.entity.PackageSettings;
import com.ykskocluk.demo.enums.DiscountType;
import com.ykskocluk.demo.enums.PackageType;
import com.ykskocluk.demo.repository.PackageCampaignRepository;
import com.ykskocluk.demo.repository.PackagePriceTierRepository;
import com.ykskocluk.demo.repository.PackageSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PackagePricingServiceTest {
    private final PackagePriceTierRepository tiers = mock(PackagePriceTierRepository.class);
    private final PackageCampaignRepository campaigns = mock(PackageCampaignRepository.class);
    private final PackageSettingsRepository settings = mock(PackageSettingsRepository.class);
    private final PackagePricingService service = new PackagePricingService(tiers, campaigns, settings);

    @Test void selectsUntilExamTierFromConfiguredExamDate() {
        Package pkg = pkg(PackageType.UNTIL_EXAM, null);
        PackageSettings config = new PackageSettings();
        config.setYksExamYear(2026);
        config.setYksExamDate(LocalDate.of(2026, 11, 2));
        config.setActive(true);
        PackagePriceTier tier = new PackagePriceTier();
        tier.setPrice(new BigDecimal("7000.00"));
        when(settings.findById(1L)).thenReturn(Optional.of(config));
        when(tiers.findByPkgIdAndMonthsRemaining(7L, 2)).thenReturn(Optional.of(tier));
        when(campaigns.findByPkgId(7L)).thenReturn(Optional.empty());

        var result = service.resolve(pkg, Instant.parse("2026-09-03T09:00:00Z"));
        assertThat(result.untilExamMonthsRemaining()).isEqualTo(2);
        assertThat(result.listPrice()).isEqualByComparingTo("7000.00");
        assertThat(result.yksExamYear()).isEqualTo(2026);
        assertThat(result.yksExamDate()).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test void calendarMonthBoundariesRoundOnlyPartialServiceMonthsUp() {
        assertThat(PackagePricingService.monthsRemaining(
                LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28))).isEqualTo(1);
        assertThat(PackagePricingService.monthsRemaining(
                LocalDate.of(2027, 1, 31), LocalDate.of(2027, 3, 1))).isEqualTo(2);
        assertThat(PackagePricingService.monthsRemaining(
                LocalDate.of(2027, 4, 20), LocalDate.of(2027, 6, 20))).isEqualTo(2);
    }

    @Test void inactiveOrPastExamMakesUntilExamUnavailable() {
        Package pkg = pkg(PackageType.UNTIL_EXAM, null);
        PackageSettings config = exam(2026, LocalDate.of(2026, 11, 2), false);
        when(settings.findById(1L)).thenReturn(Optional.of(config));
        when(campaigns.findByPkgId(7L)).thenReturn(Optional.empty());
        assertThat(service.resolve(pkg, Instant.parse("2026-09-03T09:00:00Z")).effectivePrice()).isNull();

        config.setActive(true);
        config.setYksExamDate(LocalDate.of(2026, 8, 31));
        assertThat(service.resolve(pkg, Instant.parse("2026-09-03T09:00:00Z")).effectivePrice()).isNull();
    }

    @Test void missingExactTierDoesNotFallBackToAnotherTier() {
        Package pkg = pkg(PackageType.UNTIL_EXAM, null);
        when(settings.findById(1L)).thenReturn(Optional.of(exam(2026, LocalDate.of(2026, 11, 2), true)));
        when(tiers.findByPkgIdAndMonthsRemaining(7L, 2)).thenReturn(Optional.empty());
        when(campaigns.findByPkgId(7L)).thenReturn(Optional.empty());
        assertThat(service.resolve(pkg, Instant.parse("2026-09-03T09:00:00Z")).effectivePrice()).isNull();
    }

    @Test void percentageDiscountIsCalculatedWithMoneyScale() {
        assertThat(PackagePricingService.applyDiscount(new BigDecimal("8000"),
                DiscountType.PERCENTAGE, new BigDecimal("12.5"))).isEqualByComparingTo("7000.00");
    }

    @Test void fixedDiscountIsCalculated() {
        assertThat(PackagePricingService.applyDiscount(new BigDecimal("8000"),
                DiscountType.FIXED_AMOUNT, new BigDecimal("1500"))).isEqualByComparingTo("6500.00");
    }

    @Test void discountIsDefensivelyFlooredAtZero() {
        assertThat(PackagePricingService.applyDiscount(new BigDecimal("100"),
                DiscountType.FIXED_AMOUNT, new BigDecimal("200"))).isEqualByComparingTo("0.00");
    }

    @Test void disabledAndExpiredCampaignsAreInactive() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        PackageCampaign campaign = campaign(now.minusSeconds(3600), now.plusSeconds(3600));
        campaign.setEnabled(false);
        assertThat(PackagePricingService.isActive(campaign, now)).isFalse();
        campaign.setEnabled(true);
        campaign.setEndsAt(now.minusSeconds(1));
        assertThat(PackagePricingService.isActive(campaign, now)).isFalse();
    }

    private Package pkg(PackageType type, BigDecimal price) {
        Package pkg = new Package();
        ReflectionTestUtils.setField(pkg, "id", 7L);
        pkg.setPackageType(type);
        pkg.setPrice(price);
        return pkg;
    }

    private PackageCampaign campaign(Instant start, Instant end) {
        PackageCampaign campaign = new PackageCampaign();
        campaign.setEnabled(true);
        campaign.setStartsAt(start);
        campaign.setEndsAt(end);
        return campaign;
    }

    private PackageSettings exam(int year, LocalDate date, boolean active) {
        PackageSettings settings = new PackageSettings();
        settings.setYksExamYear(year);
        settings.setYksExamDate(date);
        settings.setActive(active);
        return settings;
    }
}
