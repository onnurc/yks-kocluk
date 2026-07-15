package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.ConsentCreateRequest;
import com.ykskocluk.demo.dto.ConsentResponse;
import com.ykskocluk.demo.dto.ConsentStatusResponse;
import com.ykskocluk.demo.service.ConsentService;
import com.ykskocluk.demo.enums.ConsentType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for users to record their consent to legal documents (KVKK, TERMS, etc).
 */
@RestController
@RequestMapping("/api/v1/consents")
public class ConsentController {

    private final ConsentService consentService;

    public ConsentController(ConsentService consentService) {
        this.consentService = consentService;
    }

    @GetMapping("/status")
    public ConsentStatusResponse checkConsentStatus(
            @AuthenticationPrincipal Long userId,
            @RequestParam ConsentType consentType) {
        return consentService.getConsentStatus(userId, consentType);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConsentResponse recordConsent(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ConsentCreateRequest request,
            HttpServletRequest httpRequest) {
        return consentService.recordConsent(userId, request, httpRequest);
    }

    @PostMapping("/revoke")
    public ConsentResponse revokeConsent(
            @AuthenticationPrincipal Long userId,
            @RequestParam ConsentType consentType) {
        return consentService.revokeConsent(userId, consentType);
    }
}
