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
import java.util.List;
import java.util.Set;

@Service
public class TrialConsultationService {
    private static final Set<TrialConsultationStatus> ACTIVE =
            Set.of(TrialConsultationStatus.REQUESTED, TrialConsultationStatus.CONFIRMED);
    private static final Set<TrialConsultationStatus> NON_CANCELLED =
            Set.of(TrialConsultationStatus.REQUESTED, TrialConsultationStatus.CONFIRMED,
                    TrialConsultationStatus.COMPLETED, TrialConsultationStatus.NO_SHOW);
    private static final Set<SessionStatus> BLOCKING_PAID =
            Set.of(SessionStatus.PLANNED, SessionStatus.COMPLETED, SessionStatus.LATE_CANCELLED, SessionStatus.NO_SHOW);

    private final TrialConsultationRepository trials;
    private final CoachAvailabilityRepository availabilities;
    private final CoachProfileRepository coaches;
    private final SessionRepository sessions;
    private final UserRepository users;
    private final AccountReadinessService readiness;

    public TrialConsultationService(TrialConsultationRepository trials,
                                    CoachAvailabilityRepository availabilities,
                                    CoachProfileRepository coaches,
                                    SessionRepository sessions,
                                    UserRepository users,
                                    AccountReadinessService readiness) {
        this.trials = trials;
        this.availabilities = availabilities;
        this.coaches = coaches;
        this.sessions = sessions;
        this.users = users;
        this.readiness = readiness;
    }

    @Transactional
    public TrialConsultationResponse request(Long studentId, TrialConsultationCreateRequest request) {
        User student = users.findById(studentId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));
        readiness.requireReady(student);
        CoachAvailability slot = availabilities.findByIdForUpdate(request.availabilityId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND", "Uygunluk bulunamadı"));
        CoachProfile coach = slot.getCoachProfile();
        if (coach.getStatus() != CoachProfileStatus.APPROVED || coach.getUser().getStatus() != UserStatus.ACTIVE) {
            throw error(HttpStatus.FORBIDDEN, "COACH_NOT_AVAILABLE", "Koç deneme görüşmesine açık değil");
        }
        if (!slot.getStartTime().isAfter(Instant.now())) {
            throw error(HttpStatus.BAD_REQUEST, "SLOT_IN_PAST", "Geçmiş bir slot rezerve edilemez");
        }
        if (slot.isBooked()
                || sessions.existsOverlap(coach.getId(), BLOCKING_PAID, slot.getStartTime(), slot.getEndTime())
                || trials.existsActiveOverlap(coach.getId(), ACTIVE, slot.getStartTime(), slot.getEndTime())) {
            throw error(HttpStatus.CONFLICT, "SLOT_TAKEN", "Bu slot rezerve edilmiş");
        }
        if (trials.existsByStudentIdAndCoachProfileIdAndStatusIn(studentId, coach.getId(), NON_CANCELLED)) {
            throw error(HttpStatus.CONFLICT, "TRIAL_ALREADY_EXISTS",
                    "Aynı koçla yalnızca bir iptal edilmemiş deneme görüşmesi yapılabilir");
        }
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
    public TrialConsultationResponse confirm(Long coachUserId, Long id) {
        TrialConsultation trial = ownTrial(coachUserId, id);
        requireStatus(trial, TrialConsultationStatus.REQUESTED);
        trial.setStatus(TrialConsultationStatus.CONFIRMED);
        return response(trial);
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
        if (trial.getStartTime().isAfter(Instant.now())) {
            throw error(HttpStatus.CONFLICT, "TRIAL_NOT_STARTED", "Görüşme başlamadan sonuçlandırılamaz");
        }
    }

    private TrialConsultationResponse response(TrialConsultation t) {
        return new TrialConsultationResponse(t.getId(), t.getCoachProfile().getId(),
                t.getCoachProfile().getUser().getFullName(), t.getStudent().getId(), t.getStudent().getFullName(),
                t.getAvailability() == null ? null : t.getAvailability().getId(), t.getStartTime(), t.getEndTime(),
                t.getStatus(), t.getCreatedAt(), t.getUpdatedAt());
    }

    private ApiException error(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }
}
