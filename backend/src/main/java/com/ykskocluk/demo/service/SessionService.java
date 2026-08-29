package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.SessionCreateRequest;
import com.ykskocluk.demo.dto.SessionReminderView;
import com.ykskocluk.demo.dto.SessionResponse;
import com.ykskocluk.demo.entity.CoachAvailability;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Session;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.AvailabilityPurpose;
import com.ykskocluk.demo.enums.SessionStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.SessionMapper;
import com.ykskocluk.demo.repository.CoachAvailabilityRepository;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.SessionRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;

/**
 * Booking. The double-booking guarantee comes from the UNIQUE on
 * {@code sessions.availability_id} firing at insert (saveAndFlush + catch) — never from
 * reading {@code is_booked}. The weekly quota counts quota-consuming sessions in the
 * Mon–Sun (Europe/Istanbul) week containing the slot. A booking requires the student's
 * ACTIVE subscription with the coach. No external calls here — Meet/email are Phase 4d.
 */
@Service
public class SessionService {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    /** Quota-consuming statuses. The weekly-quota query (Phase 4c seam) is unchanged — only
     *  this set grew to include LATE_CANCELLED + NO_SHOW. CANCELLED (early cancel) is absent,
     *  so an early cancel returns the quota. */
    private static final Set<SessionStatus> QUOTA_STATUSES = Set.of(
            SessionStatus.PLANNED, SessionStatus.COMPLETED,
            SessionStatus.LATE_CANCELLED, SessionStatus.NO_SHOW);

    /** Below this lead time, a cancellation is LATE (slot not reopened, quota burned). */
    private static final Duration LATE_CANCEL_WINDOW = Duration.ofHours(24);

    private final SessionRepository sessionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CoachAvailabilityRepository availabilityRepository;
    private final UserRepository userRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final SessionMapper sessionMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountReadinessService accountReadinessService;

    public SessionService(SessionRepository sessionRepository,
                          SubscriptionRepository subscriptionRepository,
                          CoachAvailabilityRepository availabilityRepository,
                          UserRepository userRepository,
                          CoachProfileRepository coachProfileRepository,
                          SessionMapper sessionMapper,
                          ApplicationEventPublisher eventPublisher,
                          AccountReadinessService accountReadinessService) {
        this.sessionRepository = sessionRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.availabilityRepository = availabilityRepository;
        this.userRepository = userRepository;
        this.coachProfileRepository = coachProfileRepository;
        this.sessionMapper = sessionMapper;
        this.eventPublisher = eventPublisher;
        this.accountReadinessService = accountReadinessService;
    }

    @Transactional
    public SessionResponse book(Long studentUserId, SessionCreateRequest request) {
        User student = userRepository.findById(studentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));
        accountReadinessService.requireReady(student);

        CoachAvailability slot = availabilityRepository.findByIdForUpdate(request.availabilityId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND", "Uygunluk bulunamadı"));

        if (slot.getPurpose() != AvailabilityPurpose.PAID) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_PAID_SESSION_SLOT",
                    "Seçilen saat ücretli seans için tanımlı değil");
        }

        if (slot.isBooked()) {
            throw new ApiException(HttpStatus.CONFLICT, "SLOT_TAKEN", "Bu slot az önce rezerve edildi");
        }

        if (!slot.getStartTime().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SLOT_IN_PAST",
                    "Geçmiş bir slot rezerve edilemez");
        }

        CoachProfile coach = slot.getCoachProfile();
        if (coach.getStatus() != CoachProfileStatus.APPROVED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COACH_NOT_APPROVED",
                    "Koç henüz onaylı değil");
        }

        // (3) Must have a LIVE subscription with this coach — ACTIVE or PAST_DUE (grace window:
        // access stays open while a failed renewal is being retried). EXPIRED/CANCELLED → blocked.
        Subscription subscription = subscriptionRepository
                .findLiveSubscription(studentUserId, coach.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NO_ACTIVE_SUBSCRIPTION",
                        "Bu koç ile aktif aboneliğiniz yok"));

        // (2) Weekly quota: count quota-consuming sessions in the slot's Mon–Sun (Istanbul) week.
        enforceWeeklyQuota(subscription, slot.getStartTime());

        Session session = new Session();
        session.setStudent(student);
        session.setCoachProfile(coach);
        session.setSubscription(subscription);
        session.setAvailability(slot);
        session.setStatus(SessionStatus.PLANNED);
        session.setStartTime(slot.getStartTime());   // snapshot — survives slot unlink on cancel (4d)
        session.setEndTime(slot.getEndTime());

        try {
            // (1) The double-booking guard: UNIQUE(availability_id) fires here, inside the tx.
            // Two concurrent bookings of the same slot → exactly one insert wins; the other rolls back.
            sessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "SLOT_TAKEN", "Bu slot az önce rezerve edildi");
        }

        // (4) is_booked is a UI convenience flag, flipped inside this tx after the guard succeeds.
        slot.setBooked(true);

        // External side effects (Meet link + email) happen AFTER_COMMIT — see SessionNotificationListener.
        eventPublisher.publishEvent(new SessionBookedEvent(
                session.getId(), student.getEmail(), student.getFullName(),
                coach.getUser().getEmail(), coach.getUser().getFullName(),
                session.getStartTime(), session.getEndTime()));

        return sessionMapper.toResponse(session);
    }

    /**
     * Persists the Meet link. Called from the AFTER_COMMIT listener, so it MUST run in a
     * genuinely new transaction — a default (REQUIRED) write during after-commit processing
     * would not be committed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setMeetLink(Long sessionId, String meetLink) {
        sessionRepository.findById(sessionId).ifPresent(s -> s.setMeetLink(meetLink));
    }

    /**
     * Atomically claims a session for reminder dispatch — returns {@code true} iff this call won
     * the claim (see {@link SessionRepository#claimReminder}). Its own transaction so the claim is
     * committed before {@code SessionReminderJob} sends the mail, the same after-commit discipline
     * as {@link #setMeetLink}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimReminder(Long sessionId, Instant now) {
        return sessionRepository.claimReminder(sessionId, now) > 0;
    }

    @Transactional(readOnly = true)
    public List<SessionReminderView> findReminderCandidates(Instant now, Instant horizon) {
        return sessionRepository.findReminderCandidates(now, horizon);
    }

    /**
     * Student cancels their own PLANNED session. Early (≥24h before start) → CANCELLED, the
     * slot is reopened (link cleared + is_booked=false) and the quota is returned (CANCELLED
     * is not quota-consuming). Late (&lt;24h) → LATE_CANCELLED, slot stays booked, quota burned.
     */
    @Transactional
    public SessionResponse cancel(Long studentUserId, Long sessionId) {
        Session session = sessionRepository.findByIdAndStudentId(sessionId, studentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Seans bulunamadı"));
        requirePlanned(session);

        boolean early = Instant.now().isBefore(session.getStartTime().minus(LATE_CANCEL_WINDOW));
        if (early) {
            session.setStatus(SessionStatus.CANCELLED);
            CoachAvailability slot = session.getAvailability();
            if (slot != null) {
                slot.setBooked(false);        // reopen for others
                session.setAvailability(null); // free the UNIQUE(availability_id) for a new booking
            }
        } else {
            session.setStatus(SessionStatus.LATE_CANCELLED); // slot stays booked; quota burned
        }

        // External side effects (email) happen AFTER_COMMIT — see SessionCancellationMailListener.
        eventPublisher.publishEvent(new SessionCancelledEvent(
                session.getId(), session.getStudent().getEmail(), session.getStudent().getFullName(),
                session.getCoachProfile().getUser().getEmail(), session.getCoachProfile().getUser().getFullName(),
                session.getStartTime(), !early));

        return sessionMapper.toResponse(session);
    }

    /** Coach marks a PLANNED session COMPLETED (feeds the CoachStats total-sessions seam). */
    @Transactional
    public SessionResponse markCompleted(Long coachUserId, Long sessionId) {
        Session session = requireCoachSession(coachUserId, sessionId);
        requirePlanned(session);
        session.setStatus(SessionStatus.COMPLETED);
        return sessionMapper.toResponse(session);
    }

    /** Coach marks a PLANNED session NO_SHOW — slot not reopened, quota burned. */
    @Transactional
    public SessionResponse markNoShow(Long coachUserId, Long sessionId) {
        Session session = requireCoachSession(coachUserId, sessionId);
        requirePlanned(session);
        session.setStatus(SessionStatus.NO_SHOW);
        return sessionMapper.toResponse(session);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> myStudentSessions(Long studentUserId) {
        return sessionMapper.toResponseList(
                sessionRepository.findByStudentIdOrderByStartTimeDesc(studentUserId));
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> myCoachSessions(Long coachUserId) {
        CoachProfile profile = coachProfileRepository.findByUserId(coachUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND",
                        "Koç profili bulunamadı"));
        return sessionMapper.toResponseList(
                sessionRepository.findByCoachProfileIdOrderByStartTimeDesc(profile.getId()));
    }

    // --- helpers ---

    private Session requireCoachSession(Long coachUserId, Long sessionId) {
        CoachProfile profile = coachProfileRepository.findByUserId(coachUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND",
                        "Koç profili bulunamadı"));
        return sessionRepository.findByIdAndCoachProfileId(sessionId, profile.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Seans bulunamadı"));
    }

    private void requirePlanned(Session session) {
        if (session.getStatus() != SessionStatus.PLANNED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_SESSION_STATE",
                    "Yalnızca planlanmış seanslar üzerinde bu işlem yapılabilir");
        }
    }

    private void enforceWeeklyQuota(Subscription subscription, Instant slotStart) {
        LocalDate monday = slotStart.atZone(ISTANBUL).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant weekStart = monday.atStartOfDay(ISTANBUL).toInstant();
        Instant weekEnd = monday.plusWeeks(1).atStartOfDay(ISTANBUL).toInstant();

        long used = sessionRepository.countQuotaConsuming(
                subscription.getId(), QUOTA_STATUSES, weekStart, weekEnd);
        if (used >= subscription.getPkg().getWeeklySessions()) {
            throw new ApiException(HttpStatus.CONFLICT, "QUOTA_EXCEEDED",
                    "Bu hafta için seans kotanız doldu");
        }
    }
}
