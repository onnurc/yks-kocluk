package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.EmailVerificationProperties;
import com.ykskocluk.demo.dto.EmailVerificationResponse;
import com.ykskocluk.demo.entity.EmailVerificationCode;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.EmailVerificationCodeRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
public class EmailVerificationService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final EmailVerificationProperties properties;

    public EmailVerificationService(EmailVerificationCodeRepository codeRepository,
                                    UserRepository userRepository,
                                    PasswordEncoder passwordEncoder,
                                    ApplicationEventPublisher eventPublisher,
                                    EmailVerificationProperties properties) {
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    public void issueForRegistration(User user) {
        issue(user, false);
    }

    public void invalidateOutstanding(User user) {
        if (user != null && user.getId() != null) {
            codeRepository.invalidateAllForUser(user.getId(), now());
        }
    }

    @Transactional
    public EmailVerificationResponse resend(Long userId) {
        User user = requireUser(userId);
        if (user.isEmailVerified()) return new EmailVerificationResponse(true, null);
        Instant now = now();
        codeRepository.findFirstByUserIdOrderByCreatedAtDesc(userId).ifPresent(latest -> {
            Instant nextAllowed = latest.getCreatedAt().plus(properties.resendCooldown());
            if (now.isBefore(nextAllowed)) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "EMAIL_VERIFICATION_RESEND_TOO_SOON",
                        "Yeni kod istemeden önce bekleyin", Map.of("nextAllowedAt", nextAllowed));
            }
        });
        return issue(user, true);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public EmailVerificationResponse verify(Long userId, String rawCode) {
        User user = requireUser(userId);
        if (user.isEmailVerified()) return new EmailVerificationResponse(true, null);
        EmailVerificationCode code = codeRepository.findLatestForUpdate(userId)
                .orElseThrow(this::invalidCode);
        Instant now = now();
        if (code.getUsedAt() != null) throw invalidCode();
        if (!code.getExpiresAt().isAfter(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMAIL_VERIFICATION_CODE_EXPIRED",
                    "Doğrulama kodunun süresi dolmuş");
        }
        if (code.getAttemptCount() >= properties.maxAttempts()) throw tooManyAttempts();
        if (!passwordEncoder.matches(rawCode, code.getCodeHash())) {
            code.setAttemptCount(code.getAttemptCount() + 1);
            if (code.getAttemptCount() >= properties.maxAttempts()) throw tooManyAttempts();
            throw invalidCode();
        }
        user.setEmailVerified(true);
        codeRepository.invalidateAllForUser(userId, now);
        return new EmailVerificationResponse(true, null);
    }

    @Transactional
    protected EmailVerificationResponse issue(User user, boolean enforceUnverified) {
        if (enforceUnverified && user.isEmailVerified()) return new EmailVerificationResponse(true, null);
        Instant now = now();
        codeRepository.invalidateAllForUser(user.getId(), now);
        String rawCode = "%06d".formatted(RANDOM.nextInt(1_000_000));
        EmailVerificationCode code = new EmailVerificationCode();
        code.setUser(user);
        code.setCodeHash(passwordEncoder.encode(rawCode));
        code.setExpiresAt(now.plus(properties.codeTtl()));
        codeRepository.save(code);
        eventPublisher.publishEvent(new EmailVerificationMailEvent(user.getEmail(), rawCode));
        return new EmailVerificationResponse(false, now.plus(properties.resendCooldown()));
    }

    @Scheduled(cron = "${app.email-verification.cleanup-cron:0 30 3 * * *}", zone = "UTC")
    @Transactional
    public int cleanupRetiredCodes() {
        Instant cutoff = now().minus(properties.retention());
        return codeRepository.deleteRetired(cutoff, cutoff);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));
    }

    private ApiException invalidCode() {
        return new ApiException(HttpStatus.BAD_REQUEST, "EMAIL_VERIFICATION_CODE_INVALID",
                "Doğrulama kodu geçersiz");
    }

    private ApiException tooManyAttempts() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "EMAIL_VERIFICATION_TOO_MANY_ATTEMPTS",
                "Çok fazla hatalı deneme yapıldı; yeni bir kod isteyin");
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
