package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.*;
import com.ykskocluk.demo.enums.AccountDeletionStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.service.AccountDeletionService;
import com.ykskocluk.demo.service.LegalAcceptanceService;
import com.ykskocluk.demo.service.MarketingPreferenceService;
import com.ykskocluk.demo.service.PrivacyPreferenceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/privacy")
@PreAuthorize("isAuthenticated()")
public class PrivacyController {
    private final MarketingPreferenceService marketingPreferenceService;
    private final PrivacyPreferenceService privacyPreferenceService;
    private final LegalAcceptanceService legalAcceptanceService;
    private final AccountDeletionService accountDeletionService;

    public PrivacyController(MarketingPreferenceService marketingPreferenceService,
                             PrivacyPreferenceService privacyPreferenceService,
                             LegalAcceptanceService legalAcceptanceService,
                             AccountDeletionService accountDeletionService) {
        this.marketingPreferenceService = marketingPreferenceService;
        this.privacyPreferenceService = privacyPreferenceService;
        this.legalAcceptanceService = legalAcceptanceService;
        this.accountDeletionService = accountDeletionService;
    }

    @GetMapping("/marketing-preferences")
    public MarketingPreferencesResponse marketingPreferences(@AuthenticationPrincipal Long userId) {
        return marketingPreferenceService.get(userId);
    }

    @PatchMapping("/marketing-preferences")
    public MarketingPreferencesResponse updateMarketingPreferences(
            @AuthenticationPrincipal Long userId,
            @RequestBody MarketingPreferencesUpdateRequest request) {
        return marketingPreferenceService.update(userId, request);
    }

    @GetMapping("/preferences")
    public PrivacyPreferencesResponse privacyPreferences(@AuthenticationPrincipal Long userId) {
        return privacyPreferenceService.get(userId);
    }

    @PatchMapping("/preferences")
    public PrivacyPreferencesResponse updatePrivacyPreferences(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PrivacyPreferencesUpdateRequest request) {
        return privacyPreferenceService.update(userId, request);
    }

    @PostMapping("/explicit-consent/withdraw")
    public ExplicitConsentWithdrawalResponse withdrawExplicitConsent(@AuthenticationPrincipal Long userId) {
        return legalAcceptanceService.withdrawExplicitConsent(userId);
    }

    @PostMapping("/account-deletion")
    @ResponseStatus(HttpStatus.OK)
    public AccountDeletionResponse requestAccountDeletion(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AccountDeletionRequestRequest request) {
        AccountDeletionResponse response = accountDeletionService.requestAndComplete(userId, request);
        if (response.status() == AccountDeletionStatus.FAILED) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ACCOUNT_DELETION_FAILED",
                    "Hesap silme işlemi tamamlanamadı; hesap erişimi güvenlik amacıyla engellendi");
        }
        return response;
    }

    @GetMapping("/account-deletion")
    public AccountDeletionResponse accountDeletion(@AuthenticationPrincipal Long userId) {
        return accountDeletionService.get(userId);
    }
}
