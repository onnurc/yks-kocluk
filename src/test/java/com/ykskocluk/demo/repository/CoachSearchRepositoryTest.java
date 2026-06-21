package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.TestcontainersConfiguration;
import com.ykskocluk.demo.config.JpaAuditingConfig;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.CoachSubject;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.Track;
import com.ykskocluk.demo.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class CoachSearchRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired CoachSubjectRepository coachSubjectRepository;

    private final Pageable page = PageRequest.of(0, 20, Sort.by("createdAt").descending());

    private University uni(String name) {
        University u = new University();
        u.setName(name);
        return universityRepository.save(u);
    }

    private CoachProfile coach(String email, String fullName, String headline,
                               CoachProfileStatus status, University university, Track track) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(Role.COACH);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        CoachProfile profile = new CoachProfile();
        profile.setUser(user);
        profile.setUniversity(university);
        profile.setHeadline(headline);
        profile.setStatus(status);
        profile.setMaxStudentCapacity(10);
        coachProfileRepository.save(profile);

        CoachSubject subject = new CoachSubject();
        subject.setCoachProfile(profile);
        subject.setTrack(track);
        coachSubjectRepository.save(subject);
        return profile;
    }

    @Test
    void search_returnsOnlyApprovedAndAppliesFilters() {
        University metu = uni("METU");
        University bogazici = uni("Boğaziçi");
        CoachProfile a = coach("a@x.com", "Ahmet Yılmaz", "Matematik koçu",
                CoachProfileStatus.APPROVED, metu, Track.NUMERICAL);
        CoachProfile b = coach("b@x.com", "Beste Kaya", "Edebiyat koçu",
                CoachProfileStatus.APPROVED, bogazici, Track.VERBAL);
        coach("c@x.com", "Cem Demir", "Bekleyen koç",
                CoachProfileStatus.PENDING, metu, Track.NUMERICAL); // must never appear

        // all approved
        assertThat(coachProfileRepository.search(null, null, null, page))
                .extracting(CoachProfile::getId).containsExactlyInAnyOrder(a.getId(), b.getId());

        // by track
        assertThat(coachProfileRepository.search(null, Track.NUMERICAL, null, page))
                .extracting(CoachProfile::getId).containsExactly(a.getId());

        // by university
        assertThat(coachProfileRepository.search(bogazici.getId(), null, null, page))
                .extracting(CoachProfile::getId).containsExactly(b.getId());

        // by free-text on name and headline
        assertThat(coachProfileRepository.search(null, null, "ahmet", page))
                .extracting(CoachProfile::getId).containsExactly(a.getId());
        assertThat(coachProfileRepository.search(null, null, "edebiyat", page))
                .extracting(CoachProfile::getId).containsExactly(b.getId());

        // pending coach never surfaces, even with matching filters
        assertThat(coachProfileRepository.search(metu.getId(), Track.NUMERICAL, "cem", page)).isEmpty();
    }
}
