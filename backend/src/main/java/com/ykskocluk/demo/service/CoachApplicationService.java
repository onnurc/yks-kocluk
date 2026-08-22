package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.CoachApplicationRequest;
import com.ykskocluk.demo.dto.CoachApplicationResponse;
import com.ykskocluk.demo.dto.ForgotPasswordRequest;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.entity.CoachApplication;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachApplicationStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.CoachApplicationRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Coach onboarding front door. Anyone can {@link #submit} an application (no account, no
 * OAuth — a plain form). An admin reviews it and either {@link #reject}s it or {@link #approve}s
 * it, which is the only place a {@code COACH} {@link User} account gets created (self-registration
 * as COACH is blocked in {@code AuthService.register}). Approval never sets a password directly:
 * it creates the account with an unusable placeholder hash and immediately routes through the
 * existing {@link PasswordSecurityService#forgotPassword} flow, so the coach picks their own
 * password via the same emailed reset link every other user already uses — the admin never
 * generates, sees, or transmits a raw password.
 */
@Service
public class CoachApplicationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CoachApplicationRepository coachApplicationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordSecurityService passwordSecurityService;

    public CoachApplicationService(CoachApplicationRepository coachApplicationRepository,
                                   UserRepository userRepository,
                                   PasswordEncoder passwordEncoder,
                                   PasswordSecurityService passwordSecurityService) {
        this.coachApplicationRepository = coachApplicationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordSecurityService = passwordSecurityService;
    }

    @Transactional
    public void submit(CoachApplicationRequest request) {
        String email = request.email().trim().toLowerCase();

        // Checked in order of specificity, so the applicant always sees the most useful message
        // rather than a generic one: an approved coach's email is also a User email (approve()
        // creates one), so that check must run before the general "already registered" check.
        if (coachApplicationRepository.existsByEmailIgnoreCaseAndStatus(email, CoachApplicationStatus.PENDING)) {
            throw new ApiException(HttpStatus.CONFLICT, "COACH_APPLICATION_ALREADY_PENDING",
                    "Bu e-posta ile değerlendirme aşamasında bir başvurunuz bulunuyor");
        }
        if (coachApplicationRepository.existsByEmailIgnoreCaseAndStatus(email, CoachApplicationStatus.APPROVED)) {
            throw new ApiException(HttpStatus.CONFLICT, "COACH_APPLICATION_ALREADY_APPROVED",
                    "Bu e-posta ile zaten bir koç hesabınız var, giriş yapabilirsiniz");
        }
        // Catches emails registered by any other path (student self-register, Google sign-up,
        // an admin-created account never routed through a CoachApplication). This does trade
        // away the enumeration-resistance forgot-password deliberately keeps (an anonymous
        // submitter learns the email is taken) — accepted here because a clear, actionable
        // message for the applicant matters more for this form. approve() keeps the same check
        // as a defense-in-depth backstop for the trusted admin-facing path.
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS",
                    "Bu e-posta adresi zaten bir hesaba ait. Koç başvurusu için farklı bir e-posta adresi kullanın.");
        }

        CoachApplication application = new CoachApplication();
        application.setFullName(request.fullName().trim());
        application.setEmail(email);
        application.setPhone(request.phone());
        application.setExperience(request.experience());
        application.setStatus(CoachApplicationStatus.PENDING);
        coachApplicationRepository.save(application);
    }

    @Transactional(readOnly = true)
    public PageResponse<CoachApplicationResponse> list(CoachApplicationStatus status, Pageable pageable) {
        Page<CoachApplication> page = coachApplicationRepository.findByStatus(status, pageable);
        return PageResponse.from(page.map(CoachApplicationResponse::from));
    }

    @Transactional(readOnly = true)
    public CoachApplicationResponse detail(Long id) {
        return CoachApplicationResponse.from(findOrThrow(id));
    }

    @Transactional
    public CoachApplicationResponse approve(Long id) {
        CoachApplication application = findOrThrow(id);
        ensurePending(application);

        userRepository.findByEmailIgnoreCase(application.getEmail()).ifPresent(existing -> {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS",
                    "Bu e-posta zaten %s rolüyle kayıtlı (kullanıcı #%d). Devam etmeden önce mevcut hesabı inceleyin."
                            .formatted(existing.getRole(), existing.getId()));
        });

        User user = new User();
        user.setEmail(application.getEmail());
        user.setFullName(application.getFullName());
        user.setRole(Role.COACH);
        user.setStatus(UserStatus.ACTIVE);
        // Unusable placeholder — never handed to anyone. Its only purpose is making this
        // account "eligible" (PasswordSecurityService.eligible requires passwordHash != null)
        // for the reset-link flow triggered right below, which is how the coach sets their
        // real password. No account can authenticate with this value: it's discarded and
        // never logged, printed, or transmitted anywhere.
        user.setPasswordHash(passwordEncoder.encode(randomPlaceholder()));
        // Vetted by the application review itself — no separate email-ownership code step.
        user.setEmailVerified(true);
        // Same convention as a fresh Google sign-up (AuthService.upsertGoogleUser): the account
        // still needs to pass through the standard legal-onboarding gate on first login.
        user.setLegalOnboardingCompleted(false);
        userRepository.save(user);

        application.setStatus(CoachApplicationStatus.APPROVED);
        application.setReviewedAt(Instant.now());
        application.setLinkedUser(user);

        // Reuses the existing, tested forgot-password flow end to end (token, email, TTL) —
        // joins this method's transaction, so the email only fires after this commits.
        passwordSecurityService.forgotPassword(new ForgotPasswordRequest(application.getEmail()));

        return CoachApplicationResponse.from(application);
    }

    @Transactional
    public CoachApplicationResponse reject(Long id, String reason) {
        CoachApplication application = findOrThrow(id);
        ensurePending(application);
        application.setStatus(CoachApplicationStatus.REJECTED);
        application.setReviewedAt(Instant.now());
        application.setReviewNote(reason);
        return CoachApplicationResponse.from(application);
    }

    private CoachApplication findOrThrow(Long id) {
        return coachApplicationRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "COACH_APPLICATION_NOT_FOUND", "Başvuru bulunamadı"));
    }

    private void ensurePending(CoachApplication application) {
        if (application.getStatus() != CoachApplicationStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "COACH_APPLICATION_ALREADY_REVIEWED",
                    "Bu başvuru zaten değerlendirilmiş");
        }
    }

    private static String randomPlaceholder() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
