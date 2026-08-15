package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.CoachDetailResponse;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Track;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.CoachSearchMapper;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.CoachSubjectRepository;
import com.ykskocluk.demo.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachSearchServiceTest {

    @Mock CoachProfileRepository coachProfileRepository;
    @Mock CoachSubjectRepository coachSubjectRepository;
    @Mock CoachSearchMapper coachSearchMapper;
    @Mock SessionRepository sessionRepository;

    CoachSearchService service;

    @BeforeEach
    void setUp() {
        // Real CoachStatsService over a mocked SessionRepository (no COMPLETED rows → 0 sessions).
        service = new CoachSearchService(coachProfileRepository, coachSubjectRepository,
                new CoachStatsService(sessionRepository), coachSearchMapper);
    }

    @Test
    void getApprovedCoach_notApproved_throws404() {
        when(coachProfileRepository.findByIdAndStatusAndUserStatus(
                99L, CoachProfileStatus.APPROVED, UserStatus.ACTIVE))
                .thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.getApprovedCoach(99L));
        assertThat(ex.getErrorCode()).isEqualTo("COACH_NOT_FOUND");
    }

    @Test
    void getApprovedCoach_found_passesPlaceholderStatsToMapper() {
        CoachProfile profile = new CoachProfile();
        when(coachProfileRepository.findByIdAndStatusAndUserStatus(
                1L, CoachProfileStatus.APPROVED, UserStatus.ACTIVE))
                .thenReturn(Optional.of(profile));
        when(coachSubjectRepository.findByCoachProfileIdIn(List.of(1L))).thenReturn(List.of());
        when(sessionRepository.countByCoachAndStatus(any(), any())).thenReturn(List.of());
        CoachDetailResponse expected = new CoachDetailResponse(1L, 2L, "n", "h", "b", "u", "d", 2020,
                Set.of(Track.NUMERICAL), null, 0, true, null, null);
        // Placeholder seam: rating=null, totalSessions=0
        when(coachSearchMapper.toDetail(any(), any(), any(), anyInt())).thenReturn(expected);

        CoachDetailResponse result = service.getApprovedCoach(1L);
        assertThat(result).isEqualTo(expected);
        assertThat(result.rating()).isNull();
        assertThat(result.totalSessions()).isZero();
    }
}
