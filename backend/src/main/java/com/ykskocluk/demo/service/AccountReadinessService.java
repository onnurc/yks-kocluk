package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AccountReadinessService {
    private final LegalAcceptanceService legalAcceptanceService;

    public AccountReadinessService(LegalAcceptanceService legalAcceptanceService) {
        this.legalAcceptanceService = legalAcceptanceService;
    }

    public void requireReady(User user) {
        if (user != null && !user.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_VERIFICATION_REQUIRED",
                    "Devam etmek için e-posta adresinizi doğrulayın");
        }
        legalAcceptanceService.requireCompleted(user);
    }
}
