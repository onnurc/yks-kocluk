package com.ykskocluk.demo.service;

import com.ykskocluk.demo.enums.SessionStatus;
import com.ykskocluk.demo.repository.CoachSessionCount;
import com.ykskocluk.demo.repository.SessionRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The single seam for coach discovery stats (rating, total sessions). These are
 * <strong>derived at runtime, never stored</strong>.
 *
 * <p>{@code totalSessions} = COMPLETED sessions per coach, computed in one batched query
 * keyed by coach profile id. {@code rating} stays null until {@code Review} lands (later
 * phase) — wiring it is another localized change here. The search query, DTOs, controller,
 * and mapper are untouched (the Phase 3 seam holds).
 */
@Service
public class CoachStatsService {

    private final SessionRepository sessionRepository;

    public CoachStatsService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public Map<Long, CoachStats> statsFor(Collection<Long> coachProfileIds) {
        Map<Long, CoachStats> stats = new HashMap<>();
        if (coachProfileIds.isEmpty()) {
            return stats;
        }
        Map<Long, Long> completedByCoach = sessionRepository
                .countByCoachAndStatus(coachProfileIds, SessionStatus.COMPLETED).stream()
                .collect(Collectors.toMap(CoachSessionCount::coachProfileId, CoachSessionCount::count));

        for (Long id : coachProfileIds) {
            int totalSessions = completedByCoach.getOrDefault(id, 0L).intValue();
            stats.put(id, new CoachStats(null, totalSessions)); // rating null until Review exists
        }
        return stats;
    }
}
