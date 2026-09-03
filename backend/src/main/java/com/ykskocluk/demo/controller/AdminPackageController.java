package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.*;
import com.ykskocluk.demo.enums.PackageType;
import com.ykskocluk.demo.service.AdminPackageService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/packages")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPackageController {
    private final AdminPackageService service;

    public AdminPackageController(AdminPackageService service) {
        this.service = service;
    }

    @GetMapping
    public AdminPackageCatalogResponse getCatalog() { return service.getCatalog(); }

    @PutMapping("/{type}")
    public AdminPackageResponse update(@PathVariable PackageType type,
                                       @Valid @RequestBody AdminPackageUpdateRequest request) {
        return service.updatePackage(type, request);
    }

    @PatchMapping("/{type}/activation")
    public AdminPackageResponse activate(@PathVariable PackageType type,
                                         @RequestBody AdminPackageActivationRequest request) {
        return service.setActive(type, request.active());
    }

    @PutMapping("/UNTIL_EXAM/tiers/{monthsRemaining}")
    public AdminPackageResponse upsertTier(@PathVariable int monthsRemaining,
                                           @Valid @RequestBody AdminPriceTierRequest request) {
        return service.upsertTier(monthsRemaining, request);
    }

    @DeleteMapping("/UNTIL_EXAM/tiers/{monthsRemaining}")
    public AdminPackageResponse deleteTier(@PathVariable int monthsRemaining) {
        return service.deleteTier(monthsRemaining);
    }

    @PutMapping("/{type}/campaign")
    public AdminPackageResponse upsertCampaign(@PathVariable PackageType type,
                                               @Valid @RequestBody AdminCampaignRequest request) {
        return service.upsertCampaign(type, request);
    }

    @PatchMapping("/{type}/campaign/enabled")
    public AdminPackageResponse enableCampaign(@PathVariable PackageType type,
                                               @RequestBody AdminCampaignEnabledRequest request) {
        return service.setCampaignEnabled(type, request.enabled());
    }

    @PutMapping("/exam-settings")
    public AdminPackageCatalogResponse setExamSettings(@Valid @RequestBody AdminExamSettingsRequest request) {
        return service.setExamSettings(request.examYear(), request.examDate(), request.active());
    }
}
