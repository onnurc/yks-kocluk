package com.ykskocluk.demo;

import com.jayway.jsonpath.JsonPath;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.PackageRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UniversityRepository;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The child-safety message gate (mandatory critical path), end-to-end. Uses repo-built
 * users/subscriptions + minted JWTs so subscription status (active / past / pending / none)
 * is controlled precisely.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class MessageGateIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired PackageRepository packageRepository;

    private User persistUser(Role role) {
        User u = new User();
        u.setEmail(role.name().toLowerCase() + "-" + System.nanoTime() + "@example.com");
        u.setFullName(role.name() + " User");
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.save(u);
    }

    private CoachProfile persistCoach() {
        University uni = new University();
        uni.setName("Msg Uni " + System.nanoTime());
        universityRepository.save(uni);
        CoachProfile coach = new CoachProfile();
        coach.setUser(persistUser(Role.COACH));
        coach.setUniversity(uni);
        coach.setHeadline("Koç");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(5);
        coach.setActiveStudentCount(0);
        return coachProfileRepository.save(coach);
    }

    private void subscribe(User student, CoachProfile coach, SubscriptionStatus status) {
        Package pkg = packageRepository.findByActiveTrueOrderByPriceAsc().get(0);
        Subscription s = new Subscription();
        s.setStudent(student);
        s.setCoachProfile(coach);
        s.setPkg(pkg);
        s.setStatus(status);
        s.setStartAt(Instant.now().minus(Duration.ofDays(40)));
        s.setEndAt(Instant.now().minus(Duration.ofDays(10)));
        subscriptionRepository.save(s);
    }

    private String token(User u) {
        return "Bearer " + jwtService.generateAccessToken(u);
    }

    // --- Checkpoint 1: no subscription ever → 403 ---

    @Test
    void open_noSubscriptionEver_forbidden() throws Exception {
        User student = persistUser(Role.STUDENT);
        CoachProfile coach = persistCoach();

        mockMvc.perform(post("/api/v1/conversations").header("Authorization", token(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coachId\":%d}".formatted(coach.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MESSAGING_NOT_ALLOWED"));
    }

    // --- Checkpoint 2: past-only (CANCELLED) subscription still allows messaging ---

    @Test
    void open_pastOnlySubscription_allowed() throws Exception {
        User student = persistUser(Role.STUDENT);
        CoachProfile coach = persistCoach();
        subscribe(student, coach, SubscriptionStatus.CANCELLED); // past only, no active

        mockMvc.perform(post("/api/v1/conversations").header("Authorization", token(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coachId\":%d}".formatted(coach.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coachProfileId").value(coach.getId().intValue()));
    }

        @Test
        void open_pendingPaymentSubscription_forbidden() throws Exception {
                User student = persistUser(Role.STUDENT);
                CoachProfile coach = persistCoach();
                subscribe(student, coach, SubscriptionStatus.PENDING_PAYMENT);

                mockMvc.perform(post("/api/v1/conversations").header("Authorization", token(student))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"coachId\":%d}".formatted(coach.getId())))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.errorCode").value("MESSAGING_NOT_ALLOWED"));
        }

    // --- Full flow + read receipts (checkpoint 5) ---

    @Test
    void fullFlow_send_reply_history_andMarkReadFlipsOnlyOtherParty() throws Exception {
        User student = persistUser(Role.STUDENT);
        CoachProfile coach = persistCoach();
        User coachUser = coach.getUser();
        subscribe(student, coach, SubscriptionStatus.ACTIVE);

        // student opens the conversation
        String opened = mockMvc.perform(post("/api/v1/conversations").header("Authorization", token(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coachId\":%d}".formatted(coach.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int convId = JsonPath.read(opened, "$.id");

        // student sends, coach replies
        send(student, convId, "merhaba koç");
        send(coachUser, convId, "merhaba öğrenci");

        // coach marks read → only the STUDENT's message gets a readAt
        mockMvc.perform(post("/api/v1/conversations/" + convId + "/read")
                .header("Authorization", token(coachUser))).andExpect(status().isNoContent());

        String history = mockMvc.perform(get("/api/v1/conversations/" + convId + "/messages")
                        .header("Authorization", token(student)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Integer> senderIds = JsonPath.read(history, "$.content[*].senderId");
        List<Object> readAts = JsonPath.read(history, "$.content[*].readAt");
        int studentIdx = senderIds.indexOf(student.getId().intValue());
        int coachIdx = senderIds.indexOf(coachUser.getId().intValue());
        assertThat(readAts.get(studentIdx)).isNotNull();  // student's msg read by coach
        assertThat(readAts.get(coachIdx)).isNull();        // coach's own msg NOT flipped

        // student's inbox shows one unread (the coach's reply)
        mockMvc.perform(get("/api/v1/conversations").header("Authorization", token(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unreadCount").value(1));
    }

    // --- Checkpoint 3: non-participant blocked on send AND read ---

    @Test
    void nonParticipant_send_and_read_forbidden() throws Exception {
        User student = persistUser(Role.STUDENT);
        CoachProfile coach = persistCoach();
        subscribe(student, coach, SubscriptionStatus.ACTIVE);
        String opened = mockMvc.perform(post("/api/v1/conversations").header("Authorization", token(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coachId\":%d}".formatted(coach.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int convId = JsonPath.read(opened, "$.id");

        // an unrelated authenticated student
        User stranger = persistUser(Role.STUDENT);

        mockMvc.perform(post("/api/v1/conversations/" + convId + "/messages")
                        .header("Authorization", token(stranger))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"sızma\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_CONVERSATION_PARTICIPANT"));

        mockMvc.perform(post("/api/v1/conversations/" + convId + "/read")
                        .header("Authorization", token(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_CONVERSATION_PARTICIPANT"));

        mockMvc.perform(get("/api/v1/conversations/" + convId + "/messages")
                        .header("Authorization", token(stranger)))
                .andExpect(status().isForbidden());
    }

    private void send(User sender, int convId, String content) throws Exception {
        mockMvc.perform(post("/api/v1/conversations/" + convId + "/messages")
                        .header("Authorization", token(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"%s\"}".formatted(content)))
                .andExpect(status().isCreated());
    }
}
