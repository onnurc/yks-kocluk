package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.enums.DiscountType;
import com.ykskocluk.demo.enums.PackageType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PurchasePricingSnapshotTest {

    @Test
    void laterPackagePriceChangesDoNotAlterCapturedPurchaseMeaning() {
        Package pkg = new Package();
        pkg.setPackageType(PackageType.THREE_MONTHS);
        pkg.setPrice(new BigDecimal("8000.00"));

        Subscription purchase = new Subscription();
        purchase.setPkg(pkg);
        purchase.setPackageTypeSnapshot(PackageType.THREE_MONTHS);
        purchase.setListPriceSnapshot(new BigDecimal("8000.00"));
        purchase.setEffectivePriceSnapshot(new BigDecimal("7000.00"));
        purchase.setCampaignTitleSnapshot("Kayıt Kampanyası");
        purchase.setDiscountTypeSnapshot(DiscountType.FIXED_AMOUNT);
        purchase.setDiscountValueSnapshot(new BigDecimal("1000.00"));
        purchase.setOneMonthBasePriceSnapshot(new BigDecimal("3000.00"));
        purchase.setPurchasedAt(Instant.parse("2026-09-03T10:00:00Z"));

        pkg.setPrice(new BigDecimal("9500.00"));

        assertThat(purchase.getPackageTypeSnapshot()).isEqualTo(PackageType.THREE_MONTHS);
        assertThat(purchase.getListPriceSnapshot()).isEqualByComparingTo("8000.00");
        assertThat(purchase.getEffectivePriceSnapshot()).isEqualByComparingTo("7000.00");
        assertThat(purchase.getDiscountValueSnapshot()).isEqualByComparingTo("1000.00");
        assertThat(purchase.getOneMonthBasePriceSnapshot()).isEqualByComparingTo("3000.00");
        assertThat(purchase.getPurchasedAt()).isEqualTo(Instant.parse("2026-09-03T10:00:00Z"));
    }

    @Test
    void laterExamConfigurationChangesDoNotAlterCapturedUntilExamValues() {
        Subscription purchase = new Subscription();
        purchase.setPackageTypeSnapshot(PackageType.UNTIL_EXAM);
        purchase.setYksExamYearSnapshot(2027);
        purchase.setYksExamDateSnapshot(LocalDate.of(2027, 6, 20));
        purchase.setUntilExamMonthsRemainingSnapshot(3);

        com.ykskocluk.demo.entity.PackageSettings current = new com.ykskocluk.demo.entity.PackageSettings();
        current.setYksExamYear(2028);
        current.setYksExamDate(LocalDate.of(2028, 6, 18));

        assertThat(purchase.getYksExamYearSnapshot()).isEqualTo(2027);
        assertThat(purchase.getYksExamDateSnapshot()).isEqualTo(LocalDate.of(2027, 6, 20));
        assertThat(purchase.getUntilExamMonthsRemainingSnapshot()).isEqualTo(3);
    }
}
