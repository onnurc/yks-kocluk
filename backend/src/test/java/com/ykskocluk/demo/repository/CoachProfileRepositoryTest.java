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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class CoachProfileRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired CoachSubjectRepository coachSubjectRepository;

    private CoachProfile persistProfile(String email, CoachProfileStatus status) {
        User user = new User();
        user.setEmail(email);
        user.setFullName("Coach");
        user.setRole(Role.COACH);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        University uni = new University();
        uni.setName("Uni-" + email);
        universityRepository.save(uni);

        CoachProfile profile = new CoachProfile();
        profile.setUser(user);
        profile.setUniversity(uni);
        profile.setHeadline("h");
        profile.setStatus(status);
        profile.setMaxStudentCapacity(10);
        return coachProfileRepository.save(profile);
    }

    @Test
    void findByUserIdAndByStatus() {
        CoachProfile profile = persistProfile("c1@example.com", CoachProfileStatus.PENDING);

        assertThat(coachProfileRepository.findByUserId(profile.getUser().getId())).isPresent();
        assertThat(coachProfileRepository.existsByUserId(profile.getUser().getId())).isTrue();
        assertThat(coachProfileRepository.findByStatus(CoachProfileStatus.PENDING, PageRequest.of(0, 20)))
                .extracting(CoachProfile::getId).contains(profile.getId());
        assertThat(coachProfileRepository.findByStatus(CoachProfileStatus.APPROVED, PageRequest.of(0, 20)))
                .isEmpty();
    }

    @Test
    void coachSubject_uniquePerProfileAndTrack() {
        CoachProfile profile = persistProfile("c2@example.com", CoachProfileStatus.PENDING);

        CoachSubject first = new CoachSubject();
        first.setCoachProfile(profile);
        first.setTrack(Track.NUMERICAL);
        coachSubjectRepository.saveAndFlush(first);

        // Query while the transaction is still clean (a constraint violation below poisons it).
        assertThat(coachSubjectRepository.findByCoachProfileId(profile.getId())).hasSize(1);

        CoachSubject dup = new CoachSubject();
        dup.setCoachProfile(profile);
        dup.setTrack(Track.NUMERICAL);
        assertThatThrownBy(() -> coachSubjectRepository.saveAndFlush(dup))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
