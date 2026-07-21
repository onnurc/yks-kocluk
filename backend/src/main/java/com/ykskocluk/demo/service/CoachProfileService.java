package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.CoachProfileCreateRequest;
import com.ykskocluk.demo.dto.CoachProfileResponse;
import com.ykskocluk.demo.dto.CoachProfileUpdateRequest;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.CoachSubject;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Track;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.CoachProfileMapper;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.CoachSubjectRepository;
import com.ykskocluk.demo.repository.UniversityRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.TreeSet;

@Service
public class CoachProfileService {

    private static final int DEFAULT_MAX_CAPACITY = 10;
    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "id");

    private final CoachProfileRepository coachProfileRepository;
    private final CoachSubjectRepository coachSubjectRepository;
    private final UserRepository userRepository;
    private final UniversityRepository universityRepository;
    private final CoachProfileMapper coachProfileMapper;

    public CoachProfileService(CoachProfileRepository coachProfileRepository,
                               CoachSubjectRepository coachSubjectRepository,
                               UserRepository userRepository,
                               UniversityRepository universityRepository,
                               CoachProfileMapper coachProfileMapper) {
        this.coachProfileRepository = coachProfileRepository;
        this.coachSubjectRepository = coachSubjectRepository;
        this.userRepository = userRepository;
        this.universityRepository = universityRepository;
        this.coachProfileMapper = coachProfileMapper;
    }

    @Transactional
    public CoachProfileResponse createOwn(Long userId, CoachProfileCreateRequest request) {
        var existing = coachProfileRepository.findByUserId(userId);
        if (existing.isPresent()) {
            CoachProfile profile = existing.get();
            if (profile.getStatus() == CoachProfileStatus.REJECTED) {
                // Resubmission: REJECTED → PENDING with updated fields.
                University university = requireUniversity(request.universityId());
                profile.setHeadline(request.headline());
                profile.setBio(request.bio());
                profile.setUniversity(university);
                profile.setDepartment(request.department());
                profile.setGraduationYear(request.graduationYear());
                profile.setStatus(CoachProfileStatus.PENDING);
                profile.setRejectionReason(null);
                replaceTracks(profile, request.tracks());
                return coachProfileMapper.toResponse(profile, request.tracks());
            }
            throw new ApiException(HttpStatus.CONFLICT, "PROFILE_ALREADY_EXISTS",
                    "Koç profiliniz zaten mevcut");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));
        University university = requireUniversity(request.universityId());

        CoachProfile profile = new CoachProfile();
        profile.setUser(user);
        profile.setHeadline(request.headline());
        profile.setBio(request.bio());
        profile.setUniversity(university);
        profile.setDepartment(request.department());
        profile.setGraduationYear(request.graduationYear());
        profile.setStatus(CoachProfileStatus.PENDING);
        profile.setActiveStudentCount(0);
        profile.setMaxStudentCapacity(DEFAULT_MAX_CAPACITY);
        profile.setPayoutAccountReady(false);
        coachProfileRepository.save(profile);

        replaceTracks(profile, request.tracks());
        return coachProfileMapper.toResponse(profile, request.tracks());
    }

    @Transactional(readOnly = true)
    public CoachProfileResponse getOwn(Long userId) {
        CoachProfile profile = requireOwnProfile(userId);
        return coachProfileMapper.toResponse(profile, loadTracks(profile.getId()));
    }

    @Transactional
    public CoachProfileResponse updateOwn(Long userId, CoachProfileUpdateRequest request) {
        CoachProfile profile = requireOwnProfile(userId);
        profile.setHeadline(request.headline());
        profile.setBio(request.bio());
        profile.setUniversity(requireUniversity(request.universityId()));
        profile.setDepartment(request.department());
        profile.setGraduationYear(request.graduationYear());
        // Status is NOT touched here — only admin changes it (post-approval edits stay APPROVED).
        replaceTracks(profile, request.tracks());
        return coachProfileMapper.toResponse(profile, request.tracks());
    }

    @Transactional(readOnly = true)
    public PageResponse<CoachProfileResponse> listByStatus(CoachProfileStatus status, Pageable pageable) {
        validateSort(pageable);
        return PageResponse.from(coachProfileRepository.findByStatus(status, pageable)
                .map(profile -> coachProfileMapper.toResponse(profile, loadTracks(profile.getId()))));
    }

    @Transactional
    public CoachProfileResponse approve(Long profileId) {
        CoachProfile profile = requireProfile(profileId);
        requirePending(profile);
        profile.setStatus(CoachProfileStatus.APPROVED);
        profile.setRejectionReason(null);
        return coachProfileMapper.toResponse(profile, loadTracks(profile.getId()));
    }

    @Transactional
    public CoachProfileResponse reject(Long profileId, String reason) {
        CoachProfile profile = requireProfile(profileId);
        requirePending(profile);
        profile.setStatus(CoachProfileStatus.REJECTED);
        profile.setRejectionReason(reason);
        return coachProfileMapper.toResponse(profile, loadTracks(profile.getId()));
    }

    // --- helpers ---

    private void replaceTracks(CoachProfile profile, Set<Track> tracks) {
        coachSubjectRepository.deleteByCoachProfileId(profile.getId());
        for (Track track : tracks) {
            CoachSubject subject = new CoachSubject();
            subject.setCoachProfile(profile);
            subject.setTrack(track);
            coachSubjectRepository.save(subject);
        }
    }

    private Set<Track> loadTracks(Long profileId) {
        Set<Track> tracks = new TreeSet<>();
        coachSubjectRepository.findByCoachProfileId(profileId)
                .forEach(cs -> tracks.add(cs.getTrack()));
        return tracks;
    }

    private University requireUniversity(Long universityId) {
        return universityRepository.findById(universityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "UNIVERSITY_NOT_FOUND",
                        "Üniversite bulunamadı"));
    }

    private CoachProfile requireOwnProfile(Long userId) {
        return coachProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND",
                        "Koç profili bulunamadı"));
    }

    private CoachProfile requireProfile(Long profileId) {
        return coachProfileRepository.findById(profileId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND",
                        "Koç profili bulunamadı"));
    }

    private void requirePending(CoachProfile profile) {
        if (profile.getStatus() != CoachProfileStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION",
                    "Yalnızca beklemedeki profiller onaylanabilir veya reddedilebilir");
        }
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
