package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.SubscriptionCreateRequest;
import com.ykskocluk.demo.dto.SubscriptionResponse;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.SubscriptionMapper;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.PackageRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PackageRepository packageRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final UserRepository userRepository;
    private final SubscriptionMapper subscriptionMapper;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               PackageRepository packageRepository,
                               CoachProfileRepository coachProfileRepository,
                               UserRepository userRepository,
                               SubscriptionMapper subscriptionMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.packageRepository = packageRepository;
        this.coachProfileRepository = coachProfileRepository;
        this.userRepository = userRepository;
        this.subscriptionMapper = subscriptionMapper;
    }

    @Transactional
    public SubscriptionResponse subscribe(Long studentUserId, SubscriptionCreateRequest request) {
        Package pkg = packageRepository.findById(request.packageId())
                .filter(Package::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PACKAGE_NOT_FOUND", "Paket bulunamadı"));

        // APPROVED-only (non-approved coaches are invisible/unbookable).
        CoachProfile coach = coachProfileRepository.findByIdAndStatus(request.coachId(), CoachProfileStatus.APPROVED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COACH_NOT_FOUND", "Koç bulunamadı"));

        if (subscriptionRepository.existsByStudentIdAndCoachProfileIdAndStatus(
                studentUserId, coach.getId(), SubscriptionStatus.ACTIVE)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_SUBSCRIBED",
                    "Bu koç ile zaten aktif aboneliğiniz var");
        }

        // Atomic capacity guard — 0 rows means the coach is full.
        if (coachProfileRepository.incrementActiveStudentCountIfRoom(coach.getId()) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "COACH_FULL", "Koç kontenjanı dolu");
        }

        User student = userRepository.findById(studentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));

        Instant now = Instant.now();
        Subscription subscription = new Subscription();
        subscription.setStudent(student);
        subscription.setCoachProfile(coach);
        subscription.setPkg(pkg);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartAt(now);
        subscription.setEndAt(now.plus(pkg.getDurationDays(), ChronoUnit.DAYS));
        // Phase 8 auto-renew: on by default. Saved-card seam — the stub stamps a fake token now;
        // real iyzico tokenization replaces it in Stage 2 (no flow change here).
        subscription.setAutoRenew(true);
        subscription.setSavedCardToken("stub-card-token-" + UUID.randomUUID());

        try {
            // saveAndFlush so the partial-unique index (one ACTIVE per student+coach) fires now,
            // inside this tx — a same-student race that slipped past existsBy rolls back the
            // capacity increment too.
            subscriptionRepository.saveAndFlush(subscription);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_SUBSCRIBED",
                    "Bu koç ile zaten aktif aboneliğiniz var");
        }
        return subscriptionMapper.toResponse(subscription);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> mySubscriptions(Long studentUserId) {
        return subscriptionRepository.findByStudentIdOrderByCreatedAtDesc(studentUserId).stream()
                .map(subscriptionMapper::toResponse)
                .toList();
    }
}
