package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.AvailabilityCreateRequest;
import com.ykskocluk.demo.dto.AvailabilityResponse;
import com.ykskocluk.demo.entity.CoachAvailability;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.AvailabilityMapper;
import com.ykskocluk.demo.repository.CoachAvailabilityRepository;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Coach availability slots. Coaches manage their own concrete slots; students see only a
 * coach's open future slots. No booking happens here — that's Phase 4c (Session), which
 * flips {@code booked} and links the slot via Session.availability_id.
 */
@Service
public class CoachAvailabilityService {

    private final CoachAvailabilityRepository availabilityRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final AvailabilityMapper availabilityMapper;

    public CoachAvailabilityService(CoachAvailabilityRepository availabilityRepository,
                                    CoachProfileRepository coachProfileRepository,
                                    AvailabilityMapper availabilityMapper) {
        this.availabilityRepository = availabilityRepository;
        this.coachProfileRepository = coachProfileRepository;
        this.availabilityMapper = availabilityMapper;
    }

    @Transactional
    public AvailabilityResponse createOwn(Long coachUserId, AvailabilityCreateRequest request) {
        CoachProfile profile = requireOwnProfile(coachUserId);
        validateRange(request.startTime(), request.endTime());

        CoachAvailability slot = new CoachAvailability();
        slot.setCoachProfile(profile);
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        slot.setBooked(false);

        try {
            // saveAndFlush so the UNIQUE(coach_profile_id, start_time) fires now, inside the tx.
            availabilityRepository.saveAndFlush(slot);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "SLOT_DUPLICATE",
                    "Bu başlangıç saatinde zaten bir uygunluk var");
        }
        return availabilityMapper.toResponse(slot);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> listOwn(Long coachUserId) {
        CoachProfile profile = requireOwnProfile(coachUserId);
        return availabilityMapper.toResponseList(
                availabilityRepository.findByCoachProfileIdOrderByStartTimeAsc(profile.getId()));
    }

    @Transactional
    public void deleteOwn(Long coachUserId, Long availabilityId) {
        CoachProfile profile = requireOwnProfile(coachUserId);
        CoachAvailability slot = availabilityRepository.findByIdAndCoachProfileId(availabilityId, profile.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND", "Uygunluk bulunamadı"));
        if (slot.isBooked()) {
            throw new ApiException(HttpStatus.CONFLICT, "SLOT_BOOKED",
                    "Rezerve edilmiş bir uygunluk silinemez");
        }
        availabilityRepository.delete(slot);
    }

    /** Student-facing: a specific APPROVED coach's open future slots. */
    @Transactional(readOnly = true)
    public List<AvailabilityResponse> listOpenSlots(Long coachProfileId) {
        coachProfileRepository.findByIdAndStatus(coachProfileId, CoachProfileStatus.APPROVED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COACH_NOT_FOUND", "Koç bulunamadı"));
        return availabilityMapper.toResponseList(
                availabilityRepository.findByCoachProfileIdAndBookedFalseAndStartTimeAfterOrderByStartTimeAsc(
                        coachProfileId, Instant.now()));
    }

    // --- helpers ---

    private void validateRange(Instant startTime, Instant endTime) {
        if (!startTime.isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SLOT_IN_PAST",
                    "Geçmiş bir zaman için uygunluk oluşturulamaz");
        }
        if (!endTime.isAfter(startTime)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SLOT_RANGE",
                    "Bitiş zamanı başlangıçtan sonra olmalı");
        }
    }

    private CoachProfile requireOwnProfile(Long coachUserId) {
        return coachProfileRepository.findByUserId(coachUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND",
                        "Koç profili bulunamadı"));
    }
}
