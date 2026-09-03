package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.*;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.enums.DiscountType;
import com.ykskocluk.demo.enums.PackageType;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminPackageServiceTest {
    @Mock PackageRepository packages;
    @Mock PackagePriceTierRepository tiers;
    @Mock PackageCampaignRepository campaigns;
    @Mock PackageSettingsRepository settings;
    @Mock PackagePricingService pricing;
    AdminPackageService service;
    Package oneMonth;

    @BeforeEach void setUp() {
        service = new AdminPackageService(packages, tiers, campaigns, settings, pricing);
        oneMonth = new Package();
        ReflectionTestUtils.setField(oneMonth, "id", 1L);
        oneMonth.setPackageType(PackageType.ONE_MONTH);
        oneMonth.setName("1 Aylık");
        oneMonth.setPrice(new BigDecimal("3000"));
        lenient().when(packages.findByPackageType(PackageType.ONE_MONTH)).thenReturn(Optional.of(oneMonth));
        lenient().when(pricing.resolve(any(), any())).thenReturn(new PackagePricingService.PriceResolution(
                new BigDecimal("3000"), new BigDecimal("3000"), null, null, null, false, null));
    }

    @Test void updatesPriceAndActivationTogether() {
        when(campaigns.findByPkgId(1L)).thenReturn(Optional.empty());
        when(packages.save(oneMonth)).thenReturn(oneMonth);
        var response = service.updatePackage(PackageType.ONE_MONTH,
                new AdminPackageUpdateRequest(new BigDecimal("3500"), true));
        assertThat(oneMonth.getPrice()).isEqualByComparingTo("3500.00");
        assertThat(oneMonth.isActive()).isTrue();
        assertThat(response.packageType()).isEqualTo(PackageType.ONE_MONTH);
    }

    @Test void rejectsInvalidCampaignDateRange() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        var request = new AdminCampaignRequest(true, "Kampanya", null, now, now,
                DiscountType.PERCENTAGE, BigDecimal.TEN);
        assertThatThrownBy(() -> service.upsertCampaign(PackageType.ONE_MONTH, request))
                .isInstanceOf(ApiException.class).hasMessageContaining("bitiş zamanı");
    }

    @Test void rejectsFixedDiscountThatWouldProduceNegativePrice() {
        when(campaigns.findByPkgId(1L)).thenReturn(Optional.empty());
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        var request = new AdminCampaignRequest(true, "Kampanya", null, now, now.plusSeconds(3600),
                DiscountType.FIXED_AMOUNT, new BigDecimal("4000"));
        assertThatThrownBy(() -> service.upsertCampaign(PackageType.ONE_MONTH, request))
                .isInstanceOf(ApiException.class).hasMessageContaining("büyük olamaz");
        verify(campaigns, never()).save(any());
    }

    @Test void updatesTheExistingSingletonExamConfiguration() {
        com.ykskocluk.demo.entity.PackageSettings current = new com.ykskocluk.demo.entity.PackageSettings();
        when(settings.findById(1L)).thenReturn(Optional.of(current));
        when(packages.findByPackageTypeIsNotNullOrderByIdAsc()).thenReturn(List.of());

        var response = service.setExamSettings(2027, LocalDate.of(2027, 6, 20), true);

        assertThat(current.getYksExamYear()).isEqualTo(2027);
        assertThat(current.getYksExamDate()).isEqualTo(LocalDate.of(2027, 6, 20));
        assertThat(current.isActive()).isTrue();
        assertThat(response.yksExamActive()).isTrue();
        verify(settings).save(current);
        verify(settings, times(2)).findById(1L);
    }
}
