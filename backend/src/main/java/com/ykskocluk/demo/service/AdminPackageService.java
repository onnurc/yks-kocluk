package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.*;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.PackageCampaign;
import com.ykskocluk.demo.entity.PackagePriceTier;
import com.ykskocluk.demo.entity.PackageSettings;
import com.ykskocluk.demo.enums.DiscountType;
import com.ykskocluk.demo.enums.PackageType;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.PackageCampaignRepository;
import com.ykskocluk.demo.repository.PackagePriceTierRepository;
import com.ykskocluk.demo.repository.PackageRepository;
import com.ykskocluk.demo.repository.PackageSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class AdminPackageService {
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private final PackageRepository packageRepository;
    private final PackagePriceTierRepository tierRepository;
    private final PackageCampaignRepository campaignRepository;
    private final PackageSettingsRepository settingsRepository;
    private final PackagePricingService pricingService;

    public AdminPackageService(PackageRepository packageRepository,
                               PackagePriceTierRepository tierRepository,
                               PackageCampaignRepository campaignRepository,
                               PackageSettingsRepository settingsRepository,
                               PackagePricingService pricingService) {
        this.packageRepository = packageRepository;
        this.tierRepository = tierRepository;
        this.campaignRepository = campaignRepository;
        this.settingsRepository = settingsRepository;
        this.pricingService = pricingService;
    }

    @Transactional(readOnly = true)
    public AdminPackageCatalogResponse getCatalog() {
        PackageSettings settings = settingsRepository.findById(1L).orElseThrow();
        Integer months = settings.isActive()
                ? PackagePricingService.monthsRemaining(LocalDate.now(ISTANBUL), settings.getYksExamDate()) : null;
        List<AdminPackageResponse> packages = packageRepository.findByPackageTypeIsNotNullOrderByIdAsc().stream()
                .map(this::toAdminResponse).toList();
        return new AdminPackageCatalogResponse(settings.getYksExamYear(), settings.getYksExamDate(),
                settings.isActive(), months, packages);
    }

    @Transactional
    public AdminPackageResponse updatePackage(PackageType type, AdminPackageUpdateRequest request) {
        if (type == PackageType.UNTIL_EXAM) {
            throw bad("UNTIL_EXAM_TIER_REQUIRED", "Sınava Kadar fiyatı ay bazlı fiyat tablosundan yönetilmelidir");
        }
        Package pkg = requirePackage(type);
        validateCampaignAgainstPrices(campaignRepository.findByPkgId(pkg.getId()).orElse(null), List.of(request.price()));
        pkg.setPrice(request.price().setScale(2));
        pkg.setActive(request.active());
        return toAdminResponse(packageRepository.save(pkg));
    }

    @Transactional
    public AdminPackageResponse setActive(PackageType type, boolean active) {
        Package pkg = requirePackage(type);
        if (active && type != PackageType.UNTIL_EXAM && pkg.getPrice() == null) {
            throw bad("PACKAGE_PRICE_NOT_CONFIGURED", "Paket etkinleştirilmeden önce fiyat tanımlanmalıdır");
        }
        if (active && type == PackageType.UNTIL_EXAM
                && tierRepository.findByPkgIdOrderByMonthsRemainingAsc(pkg.getId()).isEmpty()) {
            throw bad("PACKAGE_TIERS_NOT_CONFIGURED", "Paket etkinleştirilmeden önce en az bir ay fiyatı tanımlanmalıdır");
        }
        pkg.setActive(active);
        return toAdminResponse(packageRepository.save(pkg));
    }

    @Transactional
    public AdminPackageResponse upsertTier(int monthsRemaining, AdminPriceTierRequest request) {
        if (monthsRemaining <= 0) throw bad("INVALID_MONTHS_REMAINING", "Kalan ay sayısı pozitif olmalıdır");
        Package pkg = requirePackage(PackageType.UNTIL_EXAM);
        PackageCampaign campaign = campaignRepository.findByPkgId(pkg.getId()).orElse(null);
        validateCampaignAgainstPrices(campaign, List.of(request.price()));
        PackagePriceTier tier = tierRepository.findByPkgIdAndMonthsRemaining(pkg.getId(), monthsRemaining)
                .orElseGet(PackagePriceTier::new);
        tier.setPkg(pkg);
        tier.setMonthsRemaining(monthsRemaining);
        tier.setPrice(request.price().setScale(2));
        tierRepository.save(tier);
        return toAdminResponse(pkg);
    }

    @Transactional
    public AdminPackageResponse deleteTier(int monthsRemaining) {
        Package pkg = requirePackage(PackageType.UNTIL_EXAM);
        tierRepository.deleteByPkgIdAndMonthsRemaining(pkg.getId(), monthsRemaining);
        return toAdminResponse(pkg);
    }

    @Transactional
    public AdminPackageResponse upsertCampaign(PackageType type, AdminCampaignRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw bad("INVALID_CAMPAIGN_DATES", "Kampanya bitiş zamanı başlangıç zamanından sonra olmalıdır");
        }
        if (request.discountType() == DiscountType.PERCENTAGE
                && request.discountValue().compareTo(new BigDecimal("100")) > 0) {
            throw bad("INVALID_CAMPAIGN_DISCOUNT", "Yüzde indirim 100 değerini aşamaz");
        }
        Package pkg = requirePackage(type);
        PackageCampaign campaign = campaignRepository.findByPkgId(pkg.getId()).orElseGet(PackageCampaign::new);
        campaign.setPkg(pkg);
        campaign.setEnabled(request.enabled());
        campaign.setTitle(request.title().trim());
        campaign.setDescription(blankToNull(request.description()));
        campaign.setStartsAt(request.startsAt());
        campaign.setEndsAt(request.endsAt());
        campaign.setDiscountType(request.discountType());
        campaign.setDiscountValue(request.discountValue().setScale(2));
        validateCampaignAgainstPrices(campaign, configuredPrices(pkg));
        campaignRepository.save(campaign);
        return toAdminResponse(pkg);
    }

    @Transactional
    public AdminPackageResponse setCampaignEnabled(PackageType type, boolean enabled) {
        Package pkg = requirePackage(type);
        PackageCampaign campaign = campaignRepository.findByPkgId(pkg.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND", "Kampanya bulunamadı"));
        if (enabled) validateCampaignAgainstPrices(campaign, configuredPrices(pkg));
        campaign.setEnabled(enabled);
        campaignRepository.save(campaign);
        return toAdminResponse(pkg);
    }

    @Transactional
    public AdminPackageCatalogResponse setExamSettings(Integer examYear, LocalDate examDate, boolean active) {
        if (examYear == null || examDate == null || examYear != examDate.getYear()) {
            throw bad("INVALID_EXAM_SETTINGS", "YKS yılı sınav tarihinin yılıyla aynı olmalıdır");
        }
        if (active && !examDate.isAfter(LocalDate.now(ISTANBUL))) {
            throw bad("INVALID_EXAM_DATE", "YKS tarihi gelecekte olmalıdır");
        }
        PackageSettings settings = settingsRepository.findById(1L).orElseThrow();
        settings.setYksExamYear(examYear);
        settings.setYksExamDate(examDate);
        settings.setActive(active);
        settingsRepository.save(settings);
        return getCatalog();
    }

    private AdminPackageResponse toAdminResponse(Package pkg) {
        PackagePricingService.PriceResolution price = pricingService.resolve(pkg, Instant.now());
        List<PackagePriceTierResponse> tiers = pkg.getPackageType() == PackageType.UNTIL_EXAM
                ? tierRepository.findByPkgIdOrderByMonthsRemainingAsc(pkg.getId()).stream()
                    .map(t -> new PackagePriceTierResponse(t.getMonthsRemaining(), t.getPrice())).toList()
                : List.of();
        return new AdminPackageResponse(pkg.getId(), pkg.getPackageType(), pkg.getName(),
                price.listPrice(), price.effectivePrice(), pkg.isActive(), pkg.getDurationMonths(),
                price.untilExamMonthsRemaining(), pkg.getEvaluationSessionsPerMonth(),
                pkg.getWeeklySessionsPerMonth(), pkg.getTotalSessionsPerMonth(), price.campaign(), tiers);
    }

    private Package requirePackage(PackageType type) {
        return packageRepository.findByPackageType(type)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PACKAGE_NOT_FOUND", "Paket bulunamadı"));
    }

    private List<BigDecimal> configuredPrices(Package pkg) {
        if (pkg.getPackageType() == PackageType.UNTIL_EXAM) {
            return tierRepository.findByPkgIdOrderByMonthsRemainingAsc(pkg.getId()).stream()
                    .map(PackagePriceTier::getPrice).toList();
        }
        return pkg.getPrice() == null ? List.of() : List.of(pkg.getPrice());
    }

    private void validateCampaignAgainstPrices(PackageCampaign campaign, List<BigDecimal> prices) {
        if (campaign == null) return;
        if (prices.isEmpty()) throw bad("PACKAGE_PRICE_NOT_CONFIGURED", "Kampanya için önce paket fiyatı tanımlanmalıdır");
        if (campaign.getDiscountType() == DiscountType.PERCENTAGE
                && campaign.getDiscountValue().compareTo(new BigDecimal("100")) > 0) {
            throw bad("INVALID_CAMPAIGN_DISCOUNT", "Yüzde indirim 100 değerini aşamaz");
        }
        if (campaign.getDiscountType() == DiscountType.FIXED_AMOUNT
                && prices.stream().anyMatch(price -> campaign.getDiscountValue().compareTo(price) > 0)) {
            throw bad("INVALID_CAMPAIGN_DISCOUNT", "Sabit indirim paket fiyatından büyük olamaz");
        }
    }

    private ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
