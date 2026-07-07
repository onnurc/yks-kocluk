package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.ConsentCreateRequest;
import com.ykskocluk.demo.dto.ConsentResponse;
import com.ykskocluk.demo.entity.ConsentRecord;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.ConsentRecordRepository;
import com.ykskocluk.demo.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service handling legal document consent recordings.
 */
@Service
public class ConsentService {

    private final ConsentRecordRepository consentRecordRepository;
    private final UserRepository userRepository;

    public ConsentService(ConsentRecordRepository consentRecordRepository, UserRepository userRepository) {
        this.consentRecordRepository = consentRecordRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ConsentResponse recordConsent(Long userId, ConsentCreateRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));

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
