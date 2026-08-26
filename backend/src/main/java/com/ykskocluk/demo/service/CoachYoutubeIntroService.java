package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.CoachYoutubeIntroResponse;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoachYoutubeIntroService {
    private final CoachProfileRepository coaches;
    private final UserRepository users;
    private final YoutubeVideoService youtubeVideos;

    public CoachYoutubeIntroService(CoachProfileRepository coaches, UserRepository users,
                                    YoutubeVideoService youtubeVideos) {
        this.coaches = coaches;
        this.users = users;
        this.youtubeVideos = youtubeVideos;
    }

    @Transactional
    public CoachYoutubeIntroResponse set(Long adminUserId, Long coachProfileId, String urlOrVideoId) {
        requireAdmin(adminUserId);
        CoachProfile coach = requireCoach(coachProfileId);
        String videoId = youtubeVideos.normalizeVideoId(urlOrVideoId);
        coach.setIntroYoutubeVideoId(videoId);
        return response(coach, videoId);
    }

    @Transactional
    public void clear(Long adminUserId, Long coachProfileId) {
        requireAdmin(adminUserId);
        requireCoach(coachProfileId).setIntroYoutubeVideoId(null);
    }

    private User requireAdmin(Long adminUserId) {
        User admin = users.findById(adminUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Admin user not found"));
        if (admin.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "Admin role is required to manage coach videos");
        }
        return admin;
    }

    private CoachProfile requireCoach(Long coachProfileId) {
        return coaches.findById(coachProfileId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COACH_PROFILE_NOT_FOUND", "Coach profile not found"));
    }

    private CoachYoutubeIntroResponse response(CoachProfile coach, String videoId) {
        return new CoachYoutubeIntroResponse(coach.getId(), videoId, youtubeVideos.embedUrl(videoId));
    }
}
