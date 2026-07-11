package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.ConsentCreateRequest;
import com.ykskocluk.demo.dto.ConsentResponse;
import com.ykskocluk.demo.dto.ConsentStatusResponse;
import com.ykskocluk.demo.entity.ConsentRecord;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.ConsentRecordRepository;
import com.ykskocluk.demo.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import com.ykskocluk.demo.enums.ConsentType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service handling legal document consent recordings.
 */
@Service
public class ConsentService {

    // Centralized server-owned application constant.
    // NOTE: The legal/product owner must replace this with the approved document version before production.
    public static final String CURRENT_KVKK_VERSION = "v1.0";

    private final ConsentRecordRepository consentRecordRepository;
    private final UserRepository userRepository;

    public ConsentService(ConsentRecordRepository consentRecordRepository, UserRepository userRepository) {
        this.consentRecordRepository = consentRecordRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public boolean hasConsented(Long userId, ConsentType consentType, String documentVersion) {
        return consentRecordRepository.existsByUserIdAndConsentTypeAndDocumentVersion(userId, consentType, documentVersion);
    }

    @Transactional(readOnly = true)
    public ConsentStatusResponse getConsentStatus(Long userId, ConsentType consentType) {
        boolean consented = consentRecordRepository.existsByUserIdAndConsentTypeAndDocumentVersion(userId, consentType, CURRENT_KVKK_VERSION);
        return new ConsentStatusResponse(CURRENT_KVKK_VERSION, consented);
    }

    @Transactional
    public ConsentResponse recordConsent(Long userId, ConsentCreateRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));

        if (request.consentType() == ConsentType.KVKK) {
            if (!CURRENT_KVKK_VERSION.equals(request.documentVersion())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT_VERSION",
                        "Geçersiz doküman versiyonu");
            }
        }

        ConsentRecord record = new ConsentRecord();
        record.setUser(user);
        record.setConsentType(request.consentType());
        record.setDocumentVersion(request.documentVersion());
        record.setAcceptedAt(Instant.now());

        if (httpRequest != null) {
            record.setIpAddress(httpRequest.getRemoteAddr());
            record.setUserAgent(httpRequest.getHeader("User-Agent"));
        }

        consentRecordRepository.saveAndFlush(record);

        return new ConsentResponse(
                record.getId(),
                record.getConsentType(),
                record.getDocumentVersion(),
                record.getAcceptedAt()
        );
    }
}
