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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

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

    private static final Set<String> ADMIN_SORT_PROPERTIES = Set.of(
            "id", "fullName", "email", "status", "createdAt", "reviewedAt");

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

        // Duplicate state remains private: the public controller always returns its one static
        // accepted response, while these checks prevent duplicate rows and provisioning.
        if (coachApplicationRepository.existsByEmailIgnoreCaseAndStatus(email, CoachApplicationStatus.PENDING)) {
            return;
        }
        if (coachApplicationRepository.existsByEmailIgnoreCaseAndStatus(email, CoachApplicationStatus.APPROVED)) {
            return;
        }
        // Catches emails registered by any other path. approve() keeps the same check as a
        // defense-in-depth backstop for the trusted admin-facing path.
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
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
        java.util.List<Sort.Order> safeOrders = pageable.getSort().stream()
                .map(order -> {
                    if (!ADMIN_SORT_PROPERTIES.contains(order.getProperty())) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SORT_PROPERTY",
                                "Desteklenmeyen sıralama alanı");
                    }
                    return order;
                })
                .toList();
        Sort safeSort = safeOrders.isEmpty() ? Sort.unsorted() : Sort.by(safeOrders);
        Pageable safePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), safeSort);
        Page<CoachApplication> page = coachApplicationRepository.findByStatus(status, safePageable);
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
