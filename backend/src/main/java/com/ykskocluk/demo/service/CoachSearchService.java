package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.CoachDetailResponse;
import com.ykskocluk.demo.dto.CoachSummaryResponse;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Track;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.CoachSearchMapper;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.CoachSubjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Read-only coach discovery. Only APPROVED coaches are visible. Derived rating/total
 * sessions come from {@link CoachStatsService} (the runtime-compute seam); tracks are
 * batch-loaded to avoid N+1.
 */
@Service
public class CoachSearchService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "id");

    private final CoachProfileRepository coachProfileRepository;
    private final CoachSubjectRepository coachSubjectRepository;
    private final CoachStatsService coachStatsService;
    private final CoachSearchMapper coachSearchMapper;

    public CoachSearchService(CoachProfileRepository coachProfileRepository,
                              CoachSubjectRepository coachSubjectRepository,
                              CoachStatsService coachStatsService,
                              CoachSearchMapper coachSearchMapper) {
        this.coachProfileRepository = coachProfileRepository;
        this.coachSubjectRepository = coachSubjectRepository;
        this.coachStatsService = coachStatsService;
        this.coachSearchMapper = coachSearchMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<CoachSummaryResponse> search(Long universityId, Track track, String q, Pageable pageable) {
        validateSort(pageable);
        Page<CoachProfile> page = coachProfileRepository.search(universityId, track, normalize(q), pageable);

        List<Long> ids = page.getContent().stream().map(CoachProfile::getId).toList();
        Map<Long, Set<Track>> tracksByProfile = loadTracks(ids);
        Map<Long, CoachStats> statsByProfile = coachStatsService.statsFor(ids);

        Page<CoachSummaryResponse> mapped = page.map(profile -> {
            CoachStats stats = statsByProfile.getOrDefault(profile.getId(), CoachStats.empty());
            Set<Track> tracks = tracksByProfile.getOrDefault(profile.getId(), Set.of());
            return coachSearchMapper.toSummary(profile, tracks, stats.rating(), stats.totalSessions());
        });
        return PageResponse.from(mapped);
    }

    @Transactional(readOnly = true)
    public CoachDetailResponse getApprovedCoach(Long id) {
        CoachProfile profile = coachProfileRepository.findByIdAndStatus(id, CoachProfileStatus.APPROVED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COACH_NOT_FOUND", "Koç bulunamadı"));
        Set<Track> tracks = loadTracks(List.of(id)).getOrDefault(id, Set.of());
        CoachStats stats = coachStatsService.statsFor(List.of(id)).getOrDefault(id, CoachStats.empty());
        return coachSearchMapper.toDetail(profile, tracks, stats.rating(), stats.totalSessions());
    }

    // --- helpers ---

    private Map<Long, Set<Track>> loadTracks(List<Long> coachProfileIds) {
        if (coachProfileIds.isEmpty()) {
            return Map.of();
        }
        return coachSubjectRepository.findByCoachProfileIdIn(coachProfileIds).stream()
                .collect(Collectors.groupingBy(
                        cs -> cs.getCoachProfile().getId(),
                        Collectors.mapping(cs -> cs.getTrack(),
                                Collectors.toCollection(TreeSet::new))));
    }

    private String normalize(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        return q.trim();
    }

    private void validateSort(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SORT_FIELD",
                        "Bu alana göre sıralama yapılamaz: " + order.getProperty());
            }
        }
    }
}
