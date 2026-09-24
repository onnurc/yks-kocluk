package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.*;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.*;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.Set;

@Service
public class AdminDashboardService {
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final Set<SubscriptionStatus> LIVE_SUBSCRIPTIONS =
            Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);
    private static final Set<ReportStatus> OPEN_REPORTS = Set.of(ReportStatus.OPEN, ReportStatus.REVIEWED);
    private static final Set<String> SESSION_STATUSES = Set.of("PLANNED", "COMPLETED", "CANCELLED",
            "LATE_CANCELLED", "NO_SHOW", "REQUESTED", "CONFIRMED");

    private final UserRepository users;
    private final CoachProfileRepository coaches;
    private final SubscriptionRepository subscriptions;
    private final PaymentRepository payments;
    private final ReportRepository reports;
    private final SessionRepository sessions;
    private final TrialConsultationRepository trials;
    private final CoachApplicationRepository coachApplications;
    private final StudentProfileRepository studentProfiles;
    private final MediaAssetUrlResolver mediaUrls;

    public AdminDashboardService(UserRepository users, CoachProfileRepository coaches,
                                 SubscriptionRepository subscriptions, PaymentRepository payments,
                                 ReportRepository reports, SessionRepository sessions,
                                 TrialConsultationRepository trials, CoachApplicationRepository coachApplications,
                                 StudentProfileRepository studentProfiles, MediaAssetUrlResolver mediaUrls) {
        this.users = users;
        this.coaches = coaches;
        this.subscriptions = subscriptions;
        this.payments = payments;
        this.reports = reports;
        this.sessions = sessions;
        this.trials = trials;
        this.coachApplications = coachApplications;
        this.studentProfiles = studentProfiles;
        this.mediaUrls = mediaUrls;
    }

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse summary() {
        Instant now = Instant.now();
        LocalDate first = now.atZone(ISTANBUL).toLocalDate().withDayOfMonth(1);
        Instant monthStart = first.atStartOfDay(ISTANBUL).toInstant();
        Instant nextMonth = first.plusMonths(1).atStartOfDay(ISTANBUL).toInstant();
        BigDecimal gross = zero(payments.sumForPeriod(PaymentType.CHARGE, PaymentStatus.SUCCESS, monthStart, nextMonth));
        BigDecimal refunds = zero(payments.sumForPeriod(PaymentType.REFUND, PaymentStatus.SUCCESS, monthStart, nextMonth));
        return new AdminDashboardSummaryResponse(
                users.countByRoleAndStatusNot(Role.STUDENT, UserStatus.DELETED),
                users.countByRoleAndStatusNot(Role.COACH, UserStatus.DELETED),
                coaches.countOperational(CoachProfileStatus.APPROVED, UserStatus.ACTIVE),
                coachApplications.countByStatus(CoachApplicationStatus.PENDING),
                subscriptions.countByStatusIn(LIVE_SUBSCRIPTIONS),
                payments.countForPeriod(PaymentType.CHARGE, PaymentStatus.SUCCESS, monthStart, nextMonth),
                gross,
                refunds,
                gross.subtract(refunds),
                reports.countByStatusIn(OPEN_REPORTS),
                sessions.countByStatusAndStartTimeAfter(SessionStatus.PLANNED, now)
                        + trials.countByStatusInAndStartTimeAfter(
                                Set.of(TrialConsultationStatus.REQUESTED, TrialConsultationStatus.CONFIRMED), now),
                sessions.countByStatusAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                        SessionStatus.COMPLETED, monthStart, nextMonth));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserDirectoryResponse> users(Role role, UserStatus status, String search,
                                                          Pageable pageable) {
        validateSort(pageable, Set.of("createdAt", "id", "fullName", "email"));
        String normalizedSearch = normalize(search);
        Page<User> page;
        if (role == null) {
            page = users.searchAdmin(null, status, normalizedSearch, pageable);
        } else if (status == null && normalizedSearch == null) {
            page = users.findByRole(role, pageable);
        } else if (status != null && normalizedSearch == null) {
            page = users.findByRoleAndStatus(role, status, pageable);
        } else if (status == null) {
            page = users.searchByRole(role, normalizedSearch, pageable);
        } else {
            page = users.searchByRoleAndStatus(role, status, normalizedSearch, pageable);
        }
        return PageResponse.from(page.map(this::userResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminCoachDirectoryResponse> coaches(AdminCoachFilter filter, String search,
                                                             Pageable pageable) {
        validateSort(pageable, Set.of("createdAt", "id"));
        CoachProfileStatus profileStatus = switch (filter) {
            case PENDING -> CoachProfileStatus.PENDING;
            case APPROVED, SUSPENDED -> CoachProfileStatus.APPROVED;
            case REJECTED -> CoachProfileStatus.REJECTED;
            case ALL -> null;
        };
        UserStatus userStatus = switch (filter) {
            case APPROVED -> UserStatus.ACTIVE;
            case SUSPENDED -> UserStatus.SUSPENDED;
            default -> null;
        };
        String normalizedSearch = normalize(search);
        Page<CoachProfile> page;
        if (profileStatus == null) {
            page = normalizedSearch == null ? coaches.findAllAdmin(pageable)
                    : coaches.searchAllAdmin(normalizedSearch, pageable);
        } else if (userStatus != null) {
            page = normalizedSearch == null ? coaches.findByStatusAndUserStatus(profileStatus, userStatus, pageable)
                    : coaches.searchByStatusAndUserStatus(profileStatus, userStatus, normalizedSearch, pageable);
        } else {
            page = normalizedSearch == null ? coaches.findByStatus(profileStatus, pageable)
                    : coaches.searchByStatus(profileStatus, normalizedSearch, pageable);
        }
        return PageResponse.from(page.map(this::coachResponse));
    }

    @Transactional(readOnly = true)
    public AdminCoachDirectoryResponse coach(Long profileId) {
        return coachResponse(coaches.findById(profileId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Koç profili bulunamadı")));
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse user(Long userId) {
        User user = users.findById(userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));
        var asset = studentProfiles.findByUserId(userId).map(p -> p.getProfileImageAsset()).orElse(null);
        return new AdminUserDetailResponse(userResponse(user), mediaUrls.publicUrl(asset), mediaUrls.activeAssetId(asset));
    }

    @Transactional(readOnly = true)
    public AdminFinanceSummaryResponse finance(Instant from, Instant to) {
        validateRange(from, to);
        BigDecimal gross = zero(payments.sumForPeriod(PaymentType.CHARGE, PaymentStatus.SUCCESS, from, to));
        BigDecimal refunds = zero(payments.sumForPeriod(PaymentType.REFUND, PaymentStatus.SUCCESS, from, to));
        return new AdminFinanceSummaryResponse(from, to, gross,
                payments.countForPeriod(PaymentType.CHARGE, PaymentStatus.SUCCESS, from, to),
                payments.countForPeriod(PaymentType.CHARGE, PaymentStatus.FAILED, from, to),
                payments.countForPeriod(PaymentType.CHARGE, PaymentStatus.PENDING, from, to),
                refunds, gross.subtract(refunds));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminOperationalSessionResponse> operations(AdminSessionType type, String status,
            Long coachId, Long studentId, Instant from, Instant to, Pageable pageable) {
        validateRange(from, to);
        validateSort(pageable, Set.of("createdAt"));
        String normalizedStatus = normalize(status);
        if (normalizedStatus != null) {
            normalizedStatus = normalizedStatus.toUpperCase(java.util.Locale.ROOT);
            if (!SESSION_STATUSES.contains(normalizedStatus)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SESSION_STATUS", "Geçersiz oturum durumu");
            }
        }
        Pageable databasePage = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<AdminSessionView> page = sessions.searchAdminOperations(type.name(), normalizedStatus, coachId,
                studentId, from, to, databasePage);
        return PageResponse.from(page.map(v -> new AdminOperationalSessionResponse(v.getId(), v.getType(),
                v.getCoachProfileId(), v.getCoachUserId(), v.getCoachName(), v.getStudentId(), v.getStudentName(),
                v.getStudentEmail(), v.getStartsAt(), v.getEndsAt(), v.getStatus(), v.getSubscriptionId(),
                v.getMeetingUrl())));
    }

    private AdminUserDirectoryResponse userResponse(User u) {
        return new AdminUserDirectoryResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.getStatus(),
                u.isEmailVerified(), u.isLegalOnboardingCompleted(), u.getStatus() == UserStatus.DELETED,
                u.getCreatedAt());
    }

    private AdminCoachDirectoryResponse coachResponse(CoachProfile c) {
        return new AdminCoachDirectoryResponse(c.getId(), c.getUser().getId(), c.getId(), c.getUser().getFullName(),
                c.getUser().getEmail(), c.getStatus(), c.getStatus(), c.getUser().getStatus(),
                c.getUniversity() == null ? null : c.getUniversity().getId(),
                c.getUniversity() == null ? null : c.getUniversity().getName(), c.getDepartment(),
                c.getStatus() == CoachProfileStatus.APPROVED && c.getUser().getStatus() == UserStatus.ACTIVE,
                mediaUrls.publicUrl(c.getProfileImageAsset()), mediaUrls.activeAssetId(c.getProfileImageAsset()),
                c.getIntroYoutubeVideoId(),
                c.getCreatedAt());
    }

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && !to.isAfter(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "Bitiş başlangıçtan sonra olmalı");
        }
    }

    private void validateSort(Pageable pageable, Set<String> allowed) {
        pageable.getSort().forEach(order -> {
            if (!allowed.contains(order.getProperty())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SORT_FIELD",
                        "Bu alana göre sıralama yapılamaz: " + order.getProperty());
            }
        });
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
