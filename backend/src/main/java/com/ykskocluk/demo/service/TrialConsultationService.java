package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.TrialConsultationCreateRequest;
import com.ykskocluk.demo.dto.TrialConsultationResponse;
import com.ykskocluk.demo.entity.*;
import com.ykskocluk.demo.enums.*;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class TrialConsultationService {
    private static final Set<TrialConsultationStatus> ACTIVE =
            Set.of(TrialConsultationStatus.REQUESTED, TrialConsultationStatus.CONFIRMED);
    private static final Set<TrialConsultationStatus> NON_CANCELLED =
            Set.of(TrialConsultationStatus.REQUESTED, TrialConsultationStatus.CONFIRMED,
                    TrialConsultationStatus.COMPLETED, TrialConsultationStatus.NO_SHOW);
    private static final Duration ROLLING_LIMIT_WINDOW = Duration.ofDays(7);
    private static final Set<SessionStatus> BLOCKING_PAID =
            Set.of(SessionStatus.PLANNED, SessionStatus.COMPLETED, SessionStatus.LATE_CANCELLED, SessionStatus.NO_SHOW);

    private final TrialConsultationRepository trials;
    private final CoachAvailabilityRepository availabilities;
    private final CoachProfileRepository coaches;
    private final SessionRepository sessions;
    private final UserRepository users;
    private final SubscriptionRepository subscriptions;
    private final AccountReadinessService readiness;
    private final Clock clock;

    public TrialConsultationService(TrialConsultationRepository trials,
                                    CoachAvailabilityRepository availabilities,
                                    CoachProfileRepository coaches,
                                    SessionRepository sessions,
                                    UserRepository users,
                                    SubscriptionRepository subscriptions,
                                    AccountReadinessService readiness,
                                    Clock clock) {
        this.trials = trials;
        this.availabilities = availabilities;
        this.coaches = coaches;
        this.sessions = sessions;
        this.users = users;
        this.subscriptions = subscriptions;
        this.readiness = readiness;
        this.clock = clock;
    }

    @Transactional
    public TrialConsultationResponse request(Long studentId, TrialConsultationCreateRequest request) {
        // Serializes a student's concurrent requests so the rolling two-trial limit cannot be raced
        // by booking different coaches or slots at the same time.
        User student = users.findByIdForUpdate(studentId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));
        readiness.requireReady(student);
        if (subscriptions.existsByStudentIdAndStatusIn(studentId,
                Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE))) {
            throw error(HttpStatus.CONFLICT, "ACTIVE_COACH_EXISTS",
                    "Mevcut aktif koçluk aboneliğiniz nedeniyle ücretsiz görüşme planlayamazsınız");
        }
        CoachAvailability slot = availabilities.findByIdForUpdate(request.availabilityId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND", "Uygunluk bulunamadı"));
        CoachProfile coach = slot.getCoachProfile();
        if (slot.getPurpose() != AvailabilityPurpose.TRIAL) {
            throw error(HttpStatus.BAD_REQUEST, "NOT_TRIAL_SLOT", "Seçilen saat ücretsiz görüşme için tanımlı değil");
        }
        if (coach.getStatus() != CoachProfileStatus.APPROVED || coach.getUser().getStatus() != UserStatus.ACTIVE) {
            throw error(HttpStatus.FORBIDDEN, "COACH_NOT_AVAILABLE", "Koç deneme görüşmesine açık değil");
        }
        if (!slot.getStartTime().isAfter(clock.instant())) {
            throw error(HttpStatus.BAD_REQUEST, "SLOT_IN_PAST", "Geçmiş bir slot rezerve edilemez");
        }
        if (slot.isBooked()
                || sessions.existsOverlap(coach.getId(), BLOCKING_PAID, slot.getStartTime(), slot.getEndTime())
                || trials.existsActiveOverlap(coach.getId(), ACTIVE, slot.getStartTime(), slot.getEndTime())) {
            throw error(HttpStatus.CONFLICT, "SLOT_TAKEN", "Bu slot rezerve edilmiş");
        }
        if (trials.existsByStudentIdAndCoachProfileIdAndStatusIn(studentId, coach.getId(), ACTIVE)) {
            throw error(HttpStatus.CONFLICT, "TRIAL_ALREADY_EXISTS",
                    "Aynı koçla bekleyen veya onaylanmış bir deneme görüşmeniz zaten var");
        }
        enforceRollingStudentLimit(studentId, slot.getStartTime());
        TrialConsultation trial = new TrialConsultation();
        trial.setStudent(student);
        trial.setCoachProfile(coach);
        trial.setAvailability(slot);
        trial.setStartTime(slot.getStartTime());
        trial.setEndTime(slot.getEndTime());
        trial.setStatus(TrialConsultationStatus.REQUESTED);
        slot.setBooked(true);
        try {
            trials.saveAndFlush(trial);
        } catch (DataIntegrityViolationException ex) {
            throw error(HttpStatus.CONFLICT, "TRIAL_ALREADY_EXISTS",
                    "Bu slot veya öğrenci-koç deneme görüşmesi zaten ayrılmış");
        }
        return response(trial);
    }

    @Transactional(readOnly = true)
    public List<TrialConsultationResponse> studentTrials(Long studentId) {
        return trials.findByStudentIdOrderByStartTimeDesc(studentId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<TrialConsultationResponse> coachTrials(Long coachUserId) {
        return trials.findByCoachProfileIdOrderByStartTimeDesc(ownCoach(coachUserId).getId())
                .stream().map(this::response).toList();
    }

    @Transactional
    public AdminTrialConfirmationResult confirmByAdmin(Long adminUserId, Long id, String meetingUrl) {
        User admin = users.findById(adminUserId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));
        if (admin.getRole() != Role.ADMIN) {
            throw error(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "Bu işlem için yönetici yetkisi gerekir");
        }
        TrialConsultation trial = trials.findById(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "TRIAL_NOT_FOUND", "Deneme görüşmesi bulunamadı"));
        if (trial.getStatus() == TrialConsultationStatus.CONFIRMED) {
            if (!meetingUrl.equals(trial.getMeetingUrl())) {
                throw error(HttpStatus.CONFLICT, "TRIAL_ALREADY_CONFIRMED", "Görüşme daha önce farklı bir bağlantıyla onaylandı");
            }
            return new AdminTrialConfirmationResult(response(trial), trial.getStudent().getEmail(), false);
        }
        requireStatus(trial, TrialConsultationStatus.REQUESTED);
        trial.setMeetingUrl(meetingUrl);
        trial.setConfirmedAt(clock.instant());
        trial.setConfirmedBy(admin);
        trial.setStatus(TrialConsultationStatus.CONFIRMED);
        return new AdminTrialConfirmationResult(response(trial), trial.getStudent().getEmail(), true);
    }

    @Transactional
    public TrialConsultationResponse cancelByCoach(Long coachUserId, Long id) {
        return cancel(ownTrial(coachUserId, id));
    }

    @Transactional
    public TrialConsultationResponse cancelByStudent(Long studentId, Long id) {
        TrialConsultation trial = trials.findById(id).filter(t -> t.getStudent().getId().equals(studentId))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "TRIAL_NOT_FOUND", "Deneme görüşmesi bulunamadı"));
        return cancel(trial);
    }

    @Transactional
    public TrialConsultationResponse complete(Long coachUserId, Long id) {
        TrialConsultation trial = ownTrial(coachUserId, id);
        requireStatus(trial, TrialConsultationStatus.CONFIRMED);
        requireStarted(trial);
        trial.setStatus(TrialConsultationStatus.COMPLETED);
        return response(trial);
    }

    @Transactional
    public TrialConsultationResponse noShow(Long coachUserId, Long id) {
        TrialConsultation trial = ownTrial(coachUserId, id);
        requireStatus(trial, TrialConsultationStatus.CONFIRMED);
        requireStarted(trial);
        trial.setStatus(TrialConsultationStatus.NO_SHOW);
        return response(trial);
    }

    private TrialConsultationResponse cancel(TrialConsultation trial) {
        if (!ACTIVE.contains(trial.getStatus())) {
            throw error(HttpStatus.CONFLICT, "INVALID_TRIAL_STATE", "Bu görüşme iptal edilemez");
        }
        trial.setStatus(TrialConsultationStatus.CANCELLED);
        if (trial.getAvailability() != null) {
            trial.getAvailability().setBooked(false);
            trial.setAvailability(null);
        }
        return response(trial);
    }

    private CoachProfile ownCoach(Long userId) {
        return coaches.findByUserId(userId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Koç profili bulunamadı"));
    }

    private TrialConsultation ownTrial(Long coachUserId, Long id) {
        CoachProfile coach = ownCoach(coachUserId);
        return trials.findByIdAndCoachProfileId(id, coach.getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "TRIAL_NOT_FOUND", "Deneme görüşmesi bulunamadı"));
    }

    private void requireStatus(TrialConsultation trial, TrialConsultationStatus required) {
        if (trial.getStatus() != required) {
            throw error(HttpStatus.CONFLICT, "INVALID_TRIAL_STATE", "Geçersiz deneme görüşmesi durum geçişi");
        }
    }

    private void requireStarted(TrialConsultation trial) {
        if (trial.getStartTime().isAfter(clock.instant())) {
            throw error(HttpStatus.CONFLICT, "TRIAL_NOT_STARTED", "Görüşme başlamadan sonuçlandırılamaz");
        }
    }

    private TrialConsultationResponse response(TrialConsultation t) {
        return new TrialConsultationResponse(t.getId(), t.getCoachProfile().getId(),
                t.getCoachProfile().getUser().getFullName(), t.getStudent().getId(), t.getStudent().getFullName(),
                t.getAvailability() == null ? null : t.getAvailability().getId(), t.getStartTime(), t.getEndTime(),
                t.getStatus(), t.getMeetingUrl(), t.getCreatedAt(), t.getUpdatedAt());
    }

    private void enforceRollingStudentLimit(Long studentId, Instant candidate) {
        Instant from = candidate.minus(ROLLING_LIMIT_WINDOW);
        Instant to = candidate.plus(ROLLING_LIMIT_WINDOW);
        List<Instant> starts = new ArrayList<>(trials
                .findByStudentIdAndStatusInAndStartTimeGreaterThanEqualAndStartTimeLessThanEqual(
                        studentId, NON_CANCELLED, from, to)
                .stream().map(TrialConsultation::getStartTime).toList());
        starts.add(candidate);
        starts.sort(Comparator.naturalOrder());
        for (int left = 0; left + 2 < starts.size(); left++) {
            if (!starts.get(left + 2).isAfter(starts.get(left).plus(ROLLING_LIMIT_WINDOW))) {
                throw error(HttpStatus.CONFLICT, "TRIAL_WEEKLY_LIMIT_REACHED",
                        "Her 7 günlük dönemde en fazla 2 ücretsiz görüşme planlayabilirsiniz");
            }
        }
    }

    private ApiException error(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }
}
