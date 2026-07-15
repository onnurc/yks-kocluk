package com.ykskocluk.demo.service;

import com.ykskocluk.demo.enums.SessionStatus;
import com.ykskocluk.demo.repository.CoachSessionCount;
import com.ykskocluk.demo.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachStatsServiceTest {

    @Mock SessionRepository sessionRepository;

    @Test
    void statsFor_totalSessions_isCompletedCountPerCoach_ratingNull() {
        List<Long> ids = List.of(1L, 2L, 3L);
        when(sessionRepository.countByCoachAndStatus(eq(ids), eq(SessionStatus.COMPLETED)))
                .thenReturn(List.of(new CoachSessionCount(1L, 5L), new CoachSessionCount(3L, 2L)));

        CoachStatsService service = new CoachStatsService(sessionRepository);
        Map<Long, CoachStats> stats = service.statsFor(ids);

        assertThat(stats.get(1L).totalSessions()).isEqualTo(5);
        assertThat(stats.get(2L).totalSessions()).isZero(); // no COMPLETED rows → 0
        assertThat(stats.get(3L).totalSessions()).isEqualTo(2);
        assertThat(stats.get(1L).rating()).isNull(); // rating not wired until Review exists
    }

    @Test
    void statsFor_emptyInput_noQuery() {
        CoachStatsService service = new CoachStatsService(sessionRepository);
        assertThat(service.statsFor(List.of())).isEmpty();
        verifyNoInteractions(sessionRepository);
    }
}
