package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.CoachApplicationRequest;
import com.ykskocluk.demo.dto.CoachApplicationResponse;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.entity.CoachApplication;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.AccountOrigin;
import com.ykskocluk.demo.enums.CoachApplicationStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.CoachApplicationRepository;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.validation.EmailAddresses;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Coach onboarding front door. Anyone can {@link #submit} an application (no account, no
 * OAuth — a plain form). An admin reviews it and either {@link #reject}s it or {@link #approve}s
 * it. Approval and manual admin creation share {@link CoachAccountProvisioningService}; public
 * self-registration never accepts a role. Provisioning never sets a password directly:
 * it creates the account with an unusable placeholder hash and immediately routes through the
 * existing {@link PasswordSecurityService#forgotPassword} flow, so the coach picks their own
 * password via the same emailed reset link every other user already uses — the admin never
 * generates, sees, or transmits a raw password.
 */
@Service
public class CoachApplicationService {

    private final CoachApplicationRepository coachApplicationRepository;
    private final UserRepository userRepository;
    private final CoachAccountProvisioningService provisioningService;

    public CoachApplicationService(CoachApplicationRepository coachApplicationRepository,
                                   UserRepository userRepository,
                                   CoachAccountProvisioningService provisioningService) {
        this.coachApplicationRepository = coachApplicationRepository;
        this.userRepository = userRepository;
        this.provisioningService = provisioningService;
    }

    @Transactional
    public void submit(CoachApplicationRequest request) {
        String email = EmailAddresses.normalize(request.email());
        if (!EmailAddresses.isValid(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", "Geçerli bir e-posta girin");
        }

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

        var provisioned = provisioningService.provision(
                application.getFullName(), application.getEmail(), AccountOrigin.COACH_APPLICATION);
        User user = provisioned.user();

        application.setStatus(CoachApplicationStatus.APPROVED);
        application.setReviewedAt(Instant.now());
        application.setLinkedUser(user);

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
}
