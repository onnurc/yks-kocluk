package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.PackageResponse;
import com.ykskocluk.demo.dto.PackageOfferResponse;
import com.ykskocluk.demo.dto.PublicPackageResponse;
import com.ykskocluk.demo.mapper.PackageMapper;
import com.ykskocluk.demo.repository.PackageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Instant;

@Service
public class PackageService {

    private final PackageRepository packageRepository;
    private final PackageMapper packageMapper;
    private final PackagePricingService pricingService;

    public PackageService(PackageRepository packageRepository, PackageMapper packageMapper,
                          PackagePricingService pricingService) {
        this.packageRepository = packageRepository;
        this.packageMapper = packageMapper;
        this.pricingService = pricingService;
    }

    @Transactional(readOnly = true)
    public List<PackageResponse> listActive() {
        return packageRepository.findByActiveTrueOrderByPriceAsc().stream()
                .map(packageMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicPackageResponse> listPublicActive() {
        Instant now = Instant.now();
        return packageRepository.findByPackageTypeIsNotNullOrderByIdAsc().stream()
                .filter(com.ykskocluk.demo.entity.Package::isActive)
                .map(pkg -> toPublic(pkg, pricingService.resolve(pkg, now)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PackageOfferResponse> listCoachOffers() {
        Instant now = Instant.now();
        return packageRepository.findByPackageTypeIsNotNullOrderByIdAsc().stream()
                .map(pkg -> toOffer(pkg, pricingService.resolve(pkg, now)))
                .toList();
    }

    private PublicPackageResponse toPublic(com.ykskocluk.demo.entity.Package pkg,
                                           PackagePricingService.PriceResolution price) {
        return new PublicPackageResponse(pkg.getId(), pkg.getPackageType(), pkg.getName(),
                price.effectivePrice() != null, pkg.getDurationMonths(),
                price.untilExamMonthsRemaining(), price.listPrice(), price.effectivePrice(), price.campaignActive(),
                price.campaignActive() ? price.campaign().title() : null,
                price.campaignActive() ? price.campaign().description() : null,
                pkg.getEvaluationSessionsPerMonth(), pkg.getWeeklySessionsPerMonth(), pkg.getTotalSessionsPerMonth());
    }

    private PackageOfferResponse toOffer(com.ykskocluk.demo.entity.Package pkg,
                                         PackagePricingService.PriceResolution price) {
        boolean purchasable = pkg.isActive() && price.effectivePrice() != null;
        return new PackageOfferResponse(pkg.getId(), pkg.getPackageType(), pkg.getName(), pkg.isActive(), purchasable,
                pkg.getDurationMonths(), price.untilExamMonthsRemaining(), price.listPrice(), price.effectivePrice(),
                price.campaignActive(), price.campaignActive() ? price.campaign().title() : null,
                price.campaignActive() ? price.campaign().description() : null,
                pkg.getEvaluationSessionsPerMonth(), pkg.getWeeklySessionsPerMonth(), pkg.getTotalSessionsPerMonth());
    }
}
