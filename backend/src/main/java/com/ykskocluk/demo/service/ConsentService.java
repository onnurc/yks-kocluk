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
import com.ykskocluk.demo.enums.ConsentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

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
        return consentRecordRepository.existsByUserIdAndConsentTypeAndDocumentVersionAndStatus(
                userId, consentType, documentVersion, ConsentStatus.ACCEPTED);
    }

    @Transactional(readOnly = true)
    public ConsentStatusResponse getConsentStatus(Long userId, ConsentType consentType) {
        Optional<ConsentRecord> latest = consentRecordRepository.findFirstByUserIdAndConsentTypeOrderByAcceptedAtDesc(userId, consentType);

        if (latest.isPresent()) {
            ConsentRecord record = latest.get();
            boolean isCurrentVersion = CURRENT_KVKK_VERSION.equals(record.getDocumentVersion());
            boolean isAccepted = record.getStatus() == ConsentStatus.ACCEPTED;

            boolean hasConsented = isCurrentVersion && isAccepted;
            String statusStr = record.getStatus().name();
            if (!isCurrentVersion && isAccepted) {
                statusStr = ConsentStatus.PENDING.name();
            }
            return new ConsentStatusResponse(CURRENT_KVKK_VERSION, hasConsented, statusStr);
        } else {
            return new ConsentStatusResponse(CURRENT_KVKK_VERSION, false, ConsentStatus.PENDING.name());
        }
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
        record.setStatus(ConsentStatus.ACCEPTED);

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

    @Transactional
    public ConsentResponse revokeConsent(Long userId, ConsentType consentType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));

        // Stamp the revocation with whichever version the user's latest record for this
        // consent type actually carries — not KVKK's version, which only applies to KVKK.
        // document_version is NOT NULL, so fall back to CURRENT_KVKK_VERSION if there's no
        // prior record at all (revoking a never-granted consent — an edge case today).
        String documentVersion = consentRecordRepository
                .findFirstByUserIdAndConsentTypeOrderByAcceptedAtDesc(userId, consentType)
                .map(ConsentRecord::getDocumentVersion)
                .orElse(CURRENT_KVKK_VERSION);

        ConsentRecord record = new ConsentRecord();
        record.setUser(user);
        record.setConsentType(consentType);
        record.setDocumentVersion(documentVersion);
        record.setAcceptedAt(Instant.now());
        record.setStatus(ConsentStatus.REVOKED);

        consentRecordRepository.saveAndFlush(record);

        return new ConsentResponse(
                record.getId(),
                record.getConsentType(),
                record.getDocumentVersion(),
                record.getAcceptedAt()
        );
    }

    public void checkConsentRequiredForAction(User user) {
        if (user == null || user.getRole() != com.ykskocluk.demo.enums.Role.STUDENT) {
            return;
        }
        if (isMinor(user.getDateOfBirth())) {
            boolean consented = hasConsented(user.getId(), ConsentType.KVKK, CURRENT_KVKK_VERSION);
            if (!consented) {
                throw new ApiException(HttpStatus.FORBIDDEN, "MINOR_CONSENT_REQUIRED",
                        "18 yaş altı öğrenciler için veli onayı gereklidir");
            }
        }
    }

    public boolean isMinor(java.time.LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return false;
        }
        java.time.LocalDate now = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Istanbul"));
        int age = java.time.Period.between(dateOfBirth, now).getYears();
        return age < 18;
    }
}
