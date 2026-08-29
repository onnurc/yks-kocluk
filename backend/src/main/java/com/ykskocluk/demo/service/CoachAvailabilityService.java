package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.AvailabilityCreateRequest;
import com.ykskocluk.demo.dto.AvailabilityResponse;
import com.ykskocluk.demo.dto.TrialAvailabilityUpdateRequest;
import com.ykskocluk.demo.entity.CoachAvailability;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.AvailabilityPurpose;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.AvailabilityMapper;
import com.ykskocluk.demo.repository.CoachAvailabilityRepository;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Coach availability slots. Coaches manage their own concrete slots; students see only a
 * coach's open future slots. No booking happens here — that's Phase 4c (Session), which
 * flips {@code booked} and links the slot via Session.availability_id.
 */
@Service
public class CoachAvailabilityService {

    private static final Duration MIN_SLOT_DURATION = Duration.ofMinutes(15);
    private static final Duration MAX_SLOT_DURATION = Duration.ofHours(8);
    private static final Duration MAX_SCHEDULING_HORIZON = Duration.ofDays(366);
    private static final Duration TRIAL_SLOT_DURATION = Duration.ofMinutes(30);
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final CoachAvailabilityRepository availabilityRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final AvailabilityMapper availabilityMapper;
    private final Clock clock;

    public CoachAvailabilityService(CoachAvailabilityRepository availabilityRepository,
                                    CoachProfileRepository coachProfileRepository,
                                    AvailabilityMapper availabilityMapper,
                                    Clock clock) {
        this.availabilityRepository = availabilityRepository;
        this.coachProfileRepository = coachProfileRepository;
        this.availabilityMapper = availabilityMapper;
        this.clock = clock;
    }

    @Transactional
    public AvailabilityResponse createOwn(Long coachUserId, AvailabilityCreateRequest request) {
        CoachProfile profile = requireOwnProfile(coachUserId);
        if (profile.getStatus() != CoachProfileStatus.APPROVED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COACH_NOT_APPROVED",
                    "Koç henüz onaylı değil");
        }
        validateRange(request.startTime(), request.endTime());
        if (availabilityRepository.existsOverlapping(profile.getId(), request.startTime(), request.endTime())) {
            throw new ApiException(HttpStatus.CONFLICT, "SLOT_OVERLAP",
                    "Bu zaman aralığında çakışan bir uygunluk var");
        }

        CoachAvailability slot = new CoachAvailability();
        slot.setCoachProfile(profile);
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        slot.setBooked(false);
        slot.setPurpose(AvailabilityPurpose.PAID);

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
                        coachProfileId, clock.instant()).stream()
                        .filter(slot -> slot.getPurpose() == AvailabilityPurpose.PAID).toList());
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> listOwnTrialWindow(Long coachUserId) {
        CoachProfile profile = requireOwnProfile(coachUserId);
        Window window = trialWindow();
        return availabilityMapper.toResponseList(availabilityRepository
                .findByCoachProfileIdAndPurposeAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        profile.getId(), AvailabilityPurpose.TRIAL, window.from(), window.to()));
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> listOpenTrialSlots(Long coachProfileId) {
        coachProfileRepository.findByIdAndStatus(coachProfileId, CoachProfileStatus.APPROVED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COACH_NOT_FOUND", "Koç bulunamadı"));
        Window window = trialWindow();
        Instant after = clock.instant().isAfter(window.from()) ? clock.instant() : window.from();
        return availabilityMapper.toResponseList(availabilityRepository
                .findByCoachProfileIdAndPurposeAndBookedFalseAndStartTimeGreaterThanAndStartTimeLessThanOrderByStartTimeAsc(
                        coachProfileId, AvailabilityPurpose.TRIAL, after, window.to()));
    }

    @Transactional
    public List<AvailabilityResponse> replaceOwnTrialWindow(Long coachUserId, TrialAvailabilityUpdateRequest request) {
        CoachProfile profile = requireOwnProfile(coachUserId);
        if (profile.getStatus() != CoachProfileStatus.APPROVED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COACH_NOT_APPROVED", "Koç henüz onaylı değil");
        }
        Window window = trialWindow();
        Set<Instant> selected = new HashSet<>();
        for (Instant start : request.startTimes()) {
            validateTrialStart(start, window);
            if (!selected.add(start)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_TRIAL_SLOT", "Aynı görüşme saati birden fazla seçilemez");
            }
        }

        List<CoachAvailability> existing = availabilityRepository
                .findByCoachProfileIdAndPurposeAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        profile.getId(), AvailabilityPurpose.TRIAL, window.from(), window.to());
        Set<Instant> existingStarts = new HashSet<>();
        for (CoachAvailability slot : existing) {
            existingStarts.add(slot.getStartTime());
            if (!selected.contains(slot.getStartTime()) && !slot.isBooked()) {
                availabilityRepository.delete(slot);
            }
        }
        for (Instant start : selected) {
            if (existingStarts.contains(start)) continue;
            Instant end = start.plus(TRIAL_SLOT_DURATION);
            if (availabilityRepository.existsOverlapping(profile.getId(), start, end)) {
                throw new ApiException(HttpStatus.CONFLICT, "SLOT_OVERLAP", "Seçilen saat mevcut bir uygunlukla çakışıyor");
            }
            CoachAvailability slot = new CoachAvailability();
            slot.setCoachProfile(profile);
            slot.setStartTime(start);
            slot.setEndTime(end);
            slot.setBooked(false);
            slot.setPurpose(AvailabilityPurpose.TRIAL);
            availabilityRepository.save(slot);
        }
        try {
            availabilityRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "SLOT_CONFLICT", "Uygunluk seçimi başka bir kayıtla çakışıyor");
        }
        return listOwnTrialWindow(coachUserId);
    }

    // --- helpers ---

    private void validateRange(Instant startTime, Instant endTime) {
        Instant now = clock.instant();
        if (!startTime.isAfter(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SLOT_IN_PAST",
                    "Geçmiş bir zaman için uygunluk oluşturulamaz");
        }
        if (!endTime.isAfter(startTime)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SLOT_RANGE",
                    "Bitiş zamanı başlangıçtan sonra olmalı");
        }
        Duration duration = Duration.between(startTime, endTime);
        if (duration.compareTo(MIN_SLOT_DURATION) < 0 || duration.compareTo(MAX_SLOT_DURATION) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SLOT_DURATION",
                    "Uygunluk süresi 15 dakika ile 8 saat arasında olmalı");
        }
        if (startTime.isAfter(now.plus(MAX_SCHEDULING_HORIZON))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SLOT_TOO_FAR_IN_FUTURE",
                    "Uygunluk en fazla 366 gün ileriye oluşturulabilir");
        }
    }

    private void validateTrialStart(Instant start, Window window) {
        if (!start.isAfter(clock.instant())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SLOT_IN_PAST", "Geçmiş bir zaman seçilemez");
        }
        if (start.isBefore(window.from()) || !start.isBefore(window.to())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TRIAL_SLOT_OUTSIDE_WINDOW", "Yalnızca önümüzdeki 7 gün seçilebilir");
        }
        ZonedDateTime local = start.atZone(ISTANBUL);
        int hour = local.getHour();
        int minute = local.getMinute();
        if (local.getSecond() != 0 || local.getNano() != 0 || (minute != 0 && minute != 30)
                || hour < 9 || hour > 16) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRIAL_SLOT_TIME", "Görüşme saatleri 09:00–17:00 arasında 30 dakikalık olmalıdır");
        }
    }

    private Window trialWindow() {
        LocalDate today = clock.instant().atZone(ISTANBUL).toLocalDate();
        return new Window(today.atStartOfDay(ISTANBUL).toInstant(),
                today.plusDays(7).atStartOfDay(ISTANBUL).toInstant());
    }

    private record Window(Instant from, Instant to) { }

    private CoachProfile requireOwnProfile(Long coachUserId) {
        return coachProfileRepository.findByUserId(coachUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND",
                        "Koç profili bulunamadı"));
    }
}
