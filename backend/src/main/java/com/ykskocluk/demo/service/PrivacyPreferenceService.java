package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.PrivacyPreferencesResponse;
import com.ykskocluk.demo.dto.PrivacyPreferencesUpdateRequest;
import com.ykskocluk.demo.entity.LegalDocument;
import com.ykskocluk.demo.entity.PrivacyPreference;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.LegalDocumentType;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.PrivacyPreferenceRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PrivacyPreferenceService {
    private final PrivacyPreferenceRepository repository;
    private final UserRepository userRepository;
    private final LegalDocumentService legalDocumentService;

    public PrivacyPreferenceService(PrivacyPreferenceRepository repository, UserRepository userRepository,
                                    LegalDocumentService legalDocumentService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.legalDocumentService = legalDocumentService;
    }

    @Transactional(readOnly = true)
    public PrivacyPreferencesResponse get(Long userId) {
        return repository.findByUserId(userId).map(this::response)
                .orElseGet(() -> new PrivacyPreferencesResponse(true, false, false, null, null, null, null));
    }

    @Transactional
    public PrivacyPreferencesResponse update(Long userId, PrivacyPreferencesUpdateRequest request) {
        if (Boolean.FALSE.equals(request.necessaryAllowed())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NECESSARY_PREFERENCE_REQUIRED",
                    "Zorunlu depolama devre dışı bırakılamaz");
        }
        if (request.analyticsAllowed() == null && request.marketingAllowed() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRIVACY_PREFERENCE_UPDATE_EMPTY",
                    "En az bir isteğe bağlı tercih gönderilmelidir");
        }
        LegalDocument submitted = legalDocumentService.findById(request.cookiePolicyDocumentId());
        LegalDocument current = legalDocumentService.currentEntity(LegalDocumentType.COOKIE_POLICY);
        if (submitted.getType() != LegalDocumentType.COOKIE_POLICY || !current.getId().equals(submitted.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "PRIVACY_PREFERENCE_DOCUMENT_NOT_CURRENT",
                    "Gönderilen çerez politikası güncel değil");
        }
        User user = userRepository.findById(userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));
        PrivacyPreference preference = repository.findByUserId(userId).orElseGet(() -> {
            PrivacyPreference created = new PrivacyPreference();
            created.setUser(user);
            created.setGrantedAt(Instant.now());
            created.setSource("SELF_SERVICE");
            return created;
        });
        boolean analytics = request.analyticsAllowed() != null
                ? request.analyticsAllowed() : preference.isAnalyticsAllowed();
        boolean marketing = request.marketingAllowed() != null
                ? request.marketingAllowed() : preference.isMarketingAllowed();
        if (preference.getId() != null
                && analytics == preference.isAnalyticsAllowed()
                && marketing == preference.isMarketingAllowed()
                && current.getId().equals(preference.getCookiePolicyDocument().getId())) {
            return response(preference);
        }
        preference.setAnalyticsAllowed(analytics);
        preference.setMarketingAllowed(marketing);
        preference.setCookiePolicyDocument(current);
        preference.setPolicyVersion(current.getDocumentVersion());
        return response(repository.save(preference));
    }

    public void clearForDeletion(User user) {
        repository.findByUserId(user.getId()).ifPresent(preference -> {
            preference.setAnalyticsAllowed(false);
            preference.setMarketingAllowed(false);
            preference.setSource("ACCOUNT_DELETION");
        });
    }

    private PrivacyPreferencesResponse response(PrivacyPreference preference) {
        return new PrivacyPreferencesResponse(true, preference.isAnalyticsAllowed(), preference.isMarketingAllowed(),
                preference.getCookiePolicyDocument().getId(), preference.getPolicyVersion(),
                preference.getGrantedAt(), preference.getUpdatedAt());
    }
}
