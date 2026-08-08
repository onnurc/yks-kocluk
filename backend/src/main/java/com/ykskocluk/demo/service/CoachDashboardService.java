package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.*;
import com.ykskocluk.demo.entity.*;
import com.ykskocluk.demo.enums.*;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.SessionMapper;
import com.ykskocluk.demo.repository.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CoachDashboardService {
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final Set<SubscriptionStatus> LIVE = Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);
    private static final Set<SubscriptionStatus> HISTORICAL = Set.of(SubscriptionStatus.EXPIRED, SubscriptionStatus.CANCELLED);
    private static final Set<SessionStatus> QUOTA = Set.of(SessionStatus.PLANNED, SessionStatus.COMPLETED,
            SessionStatus.LATE_CANCELLED, SessionStatus.NO_SHOW);
    private static final Set<TrialConsultationStatus> UPCOMING_TRIAL =
            Set.of(TrialConsultationStatus.REQUESTED, TrialConsultationStatus.CONFIRMED);

    private final CoachProfileRepository coaches;
    private final SubscriptionRepository subscriptions;
    private final SessionRepository sessions;
    private final CoachAvailabilityRepository availabilities;
    private final MessageRepository messages;
    private final ConversationRepository conversations;
    private final TrialConsultationRepository trials;
    private final PaymentRepository payments;
    private final PackageRepository packageRepository;
    private final SessionMapper sessionMapper;

    public CoachDashboardService(CoachProfileRepository coaches, SubscriptionRepository subscriptions,
                                 SessionRepository sessions, CoachAvailabilityRepository availabilities,
                                 MessageRepository messages, ConversationRepository conversations,
                                 TrialConsultationRepository trials, PaymentRepository payments,
                                 PackageRepository packageRepository, SessionMapper sessionMapper) {
        this.coaches = coaches;
        this.subscriptions = subscriptions;
        this.sessions = sessions;
        this.availabilities = availabilities;
        this.messages = messages;
        this.conversations = conversations;
        this.trials = trials;
        this.payments = payments;
        this.packageRepository = packageRepository;
        this.sessionMapper = sessionMapper;
    }

    @Transactional(readOnly = true)
    public CoachDashboardSummaryResponse summary(Long coachUserId) {
        CoachProfile coach = ownCoach(coachUserId);
        Instant now = Instant.now();
        ZonedDateTime localNow = now.atZone(ISTANBUL);
        Instant monthStart = localNow.toLocalDate().withDayOfMonth(1).atStartOfDay(ISTANBUL).toInstant();
        Instant nextMonth = localNow.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay(ISTANBUL).toInstant();
        Session paid = sessions.findFirstByCoachProfileIdAndStatusAndStartTimeAfterOrderByStartTimeAsc(
                coach.getId(), SessionStatus.PLANNED, now).orElse(null);
        TrialConsultation trial = trials
                .findFirstByCoachProfileIdAndStatusInAndStartTimeAfterOrderByStartTimeAsc(coach.getId(), UPCOMING_TRIAL, now)
                .orElse(null);
        return new CoachDashboardSummaryResponse(
                subscriptions.countDistinctStudents(coach.getId(), LIVE),
                sessions.countByCoachProfileIdAndStatusAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                        coach.getId(), SessionStatus.COMPLETED, monthStart, nextMonth),
                sessions.countByCoachProfileIdAndStatusAndStartTimeAfter(coach.getId(), SessionStatus.PLANNED, now),
                messages.countUnreadForCoach(coachUserId),
                availabilities.existsByCoachProfileIdAndBookedFalseAndStartTimeAfter(coach.getId(), now),
                nextEvent(paid, trial),
                trials.countByCoachAndStatus(coach.getId(), TrialConsultationStatus.REQUESTED));
    }

    @Transactional(readOnly = true)
    public PageResponse<CoachStudentResponse> students(Long coachUserId, CoachStudentFilter filter, Pageable pageable) {
        validateSort(pageable, Set.of("createdAt", "endAt", "id"));
        CoachProfile coach = ownCoach(coachUserId);
        Set<SubscriptionStatus> statuses = switch (filter) {
            case ACTIVE -> LIVE;
            case HISTORICAL -> HISTORICAL;
            case ALL -> Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE,
                    SubscriptionStatus.EXPIRED, SubscriptionStatus.CANCELLED);
        };
        Page<Subscription> page = subscriptions.findLatestStudents(coach.getId(), statuses, pageable);
        Map<Long, Long> conversationIds = conversations.findByCoachProfileIdOrderByLastMessageAtDesc(coach.getId())
                .stream().collect(Collectors.toMap(c -> c.getStudent().getId(), Conversation::getId));
        return PageResponse.from(page.map(s -> studentResponse(coach, s, conversationIds.get(s.getStudent().getId()))));
    }

    @Transactional(readOnly = true)
    public PageResponse<SessionResponse> sessions(Long coachUserId, Instant from, Instant to,
                                                   SessionStatus status, Long studentId, Pageable pageable) {
        validateSort(pageable, Set.of("startTime", "createdAt", "id"));
        CoachProfile coach = ownCoach(coachUserId);
        if (from != null && to != null && !to.isAfter(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "Bitiş başlangıçtan sonra olmalı");
        }
        return PageResponse.from(sessions.findCoachCalendar(coach.getId(), from, to, status, studentId, pageable)
                .map(sessionMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public List<CoachConversationSummaryResponse> conversationSummaries(Long coachUserId) {
        CoachProfile coach = ownCoach(coachUserId);
        return conversations.findByCoachProfileIdOrderByLastMessageAtDesc(coach.getId()).stream().map(c -> {
            Message last = messages.findFirstByConversationIdOrderByCreatedAtDesc(c.getId()).orElse(null);
            String preview = last == null ? null : last.getContent().substring(0, Math.min(120, last.getContent().length()));
            return new CoachConversationSummaryResponse(c.getId(), c.getStudent().getId(), c.getStudent().getFullName(),
                    preview, c.getLastMessageAt(),
                    messages.countByConversationIdAndSenderIdNotAndReadAtIsNull(c.getId(), coachUserId),
                    subscriptions.existsLiveSubscription(c.getStudent().getId(), coach.getId()));
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<CoachPackageSummaryResponse> packages(Long coachUserId) {
        CoachProfile coach = ownCoach(coachUserId);
        Map<Long, List<Subscription>> byPackage = subscriptions.findByCoachAndStatuses(coach.getId(), LIVE).stream()
                .collect(Collectors.groupingBy(s -> s.getPkg().getId()));
        return packageRepository.findByActiveTrueOrderByPriceAsc().stream()
                .map(pkg -> {
                    List<Subscription> current = byPackage.getOrDefault(pkg.getId(), List.of());
                    return new CoachPackageSummaryResponse(pkg.getId(), pkg.getName(),
                            current.stream().map(s -> s.getStudent().getId()).distinct().count(),
                            current.size(), payments.grossSalesForPackage(coach.getId(), pkg.getId()));
                })
                .toList();
    }

    private CoachStudentResponse studentResponse(CoachProfile coach, Subscription subscription, Long conversationId) {
        Instant now = Instant.now();
        LocalDate monday = now.atZone(ISTANBUL).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant weekStart = monday.atStartOfDay(ISTANBUL).toInstant();
        Instant weekEnd = monday.plusWeeks(1).atStartOfDay(ISTANBUL).toInstant();
        long used = sessions.countQuotaConsuming(subscription.getId(), QUOTA, weekStart, weekEnd);
        long remaining = Math.max(0, subscription.getPkg().getWeeklySessions() - used);
        Session next = sessions.findFirstByCoachProfileIdAndStudentIdAndStatusAndStartTimeAfterOrderByStartTimeAsc(
                coach.getId(), subscription.getStudent().getId(), SessionStatus.PLANNED, now).orElse(null);
        return new CoachStudentResponse(subscription.getStudent().getId(), subscription.getStudent().getFullName(),
                subscription.getPkg().getId(), subscription.getPkg().getName(), subscription.getStatus(),
                subscription.getStartAt(), subscription.getEndAt(), used, remaining, conversationId,
                next == null ? null : sessionMapper.toResponse(next));
    }

    private CoachDashboardEventResponse nextEvent(Session paid, TrialConsultation trial) {
        if (paid == null && trial == null) return null;
        if (trial == null || (paid != null && !trial.getStartTime().isBefore(paid.getStartTime()))) {
            return new CoachDashboardEventResponse(paid.getId(), "PAID_SESSION", paid.getStartTime(), paid.getEndTime(),
                    paid.getStudent().getId(), paid.getStudent().getFullName());
        }
        return new CoachDashboardEventResponse(trial.getId(), "TRIAL_CONSULTATION", trial.getStartTime(), trial.getEndTime(),
                trial.getStudent().getId(), trial.getStudent().getFullName());
    }

    private CoachProfile ownCoach(Long userId) {
        return coaches.findByUserId(userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Koç profili bulunamadı"));
    }

    private void validateSort(Pageable pageable, Set<String> allowed) {
        pageable.getSort().forEach(order -> {
            if (!allowed.contains(order.getProperty())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SORT_FIELD",
                        "Bu alana göre sıralama yapılamaz: " + order.getProperty());
            }
        });
    }
}
