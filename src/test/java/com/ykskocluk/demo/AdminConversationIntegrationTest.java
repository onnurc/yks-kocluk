package com.ykskocluk.demo;

import com.ykskocluk.demo.dto.ConversationSummaryResponse;
import com.ykskocluk.demo.dto.MessageResponse;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Conversation;
import com.ykskocluk.demo.entity.Message;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.repository.ConversationRepository;
import com.ykskocluk.demo.repository.MessageRepository;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.UniversityRepository;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.security.JwtService;
import com.ykskocluk.demo.service.AdminConversationService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5c admin oversight, end-to-end on real PostgreSQL.
 *
 * <p>Covers: real-JWT authorization through the actual SecurityConfig (ADMIN 200 / STUDENT 403 /
 * COACH 403 / no-token 401); admin reads ANY conversation without being a participant (via
 * {@link AdminConversationService}, never the participant gate); the <em>invisible read-only</em>
 * invariant (read_at / last_message_at unchanged after an admin read); messageCount correctness on
 * a multi-conversation page; a Hibernate-Statistics proof that the list is N+1-free; and that
 * soft-deleted participants remain visible to the admin.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AdminConversationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired ConversationRepository conversationRepository;
    @Autowired MessageRepository messageRepository;
    @Autowired AdminConversationService adminConversationService;
    @Autowired EntityManagerFactory entityManagerFactory;

    // --- seeding helpers (direct repo writes; precise control over state) ---

    private User persistUser(Role role, UserStatus status) {
        User u = new User();
        u.setEmail(role.name().toLowerCase() + "-" + System.nanoTime() + "@example.com");
        u.setFullName(role.name() + " " + System.nanoTime());
        u.setRole(role);
        u.setStatus(status);
        return userRepository.save(u);
    }

    private CoachProfile persistCoach() {
        University uni = new University();
        uni.setName("Uni " + System.nanoTime());
        universityRepository.save(uni);
        CoachProfile coach = new CoachProfile();
        coach.setUser(persistUser(Role.COACH, UserStatus.ACTIVE));
        coach.setUniversity(uni);
        coach.setHeadline("Koç");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(5);
        coach.setActiveStudentCount(0);
        return coachProfileRepository.save(coach);
    }

    private Conversation persistConversation(User student, CoachProfile coach, Instant lastMessageAt) {
        Conversation c = new Conversation();
        c.setStudent(student);
        c.setCoachProfile(coach);
        c.setLastMessageAt(lastMessageAt);
        return conversationRepository.save(c);
    }

    private Message persistMessage(Conversation conversation, User sender, String content) {
        Message m = new Message();
        m.setConversation(conversation);
        m.setSender(sender);
        m.setContent(content);
        return messageRepository.save(m);
    }

    private String token(User u) {
        return "Bearer " + jwtService.generateAccessToken(u);
    }

    // --- authorization (real method security through the real SecurityConfig) ---

    @Test
    void list_admin200_student403_coach403_anon401() throws Exception {
        User admin = persistUser(Role.ADMIN, UserStatus.ACTIVE);
        User student = persistUser(Role.STUDENT, UserStatus.ACTIVE);
        CoachProfile coach = persistCoach();

        mockMvc.perform(get("/api/v1/admin/conversations").header("Authorization", token(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/conversations").header("Authorization", token(student)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/conversations").header("Authorization", token(coach.getUser())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/conversations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void messages_nonExistentConversation_404ProblemDetail() throws Exception {
        User admin = persistUser(Role.ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/admin/conversations/999999/messages")
                        .header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Auditing\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CONVERSATION_NOT_FOUND"));
    }

    // --- admin reads ANY conversation without being a participant ---

    @Test
    void admin_readsConversation_withoutBeingParticipant() throws Exception {
        User admin = persistUser(Role.ADMIN, UserStatus.ACTIVE);
        User student = persistUser(Role.STUDENT, UserStatus.ACTIVE);
        CoachProfile coach = persistCoach();
        Conversation conv = persistConversation(student, coach, Instant.now());
        persistMessage(conv, student, "merhaba koç");
        persistMessage(conv, coach.getUser(), "merhaba öğrenci");

        // admin is neither the student nor the coach of this conversation
        mockMvc.perform(post("/api/v1/admin/conversations/" + conv.getId() + "/messages")
                        .header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Auditing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    // --- MOST CRITICAL: admin read is invisible (no read_at / last_message_at mutation) ---

    @Test
    void adminRead_doesNotMutate_readAt_or_lastMessageAt() {
        User admin = persistUser(Role.ADMIN, UserStatus.ACTIVE);
        User student = persistUser(Role.STUDENT, UserStatus.ACTIVE);
        CoachProfile coach = persistCoach();
        Instant originalLastMessageAt = Instant.now().minus(Duration.ofHours(3));
        Conversation conv = persistConversation(student, coach, originalLastMessageAt);

        Message m1 = persistMessage(conv, student, "öğrenci mesajı");
        Message m2 = persistMessage(conv, coach.getUser(), "koç cevabı");
        // mark one as read, leave the other unread — capture both states up front
        Instant readStamp = Instant.now().minus(Duration.ofHours(1));
        m1.setReadAt(readStamp);
        messageRepository.save(m1);

        Instant m1ReadBefore = messageRepository.findById(m1.getId()).orElseThrow().getReadAt();
        Instant m2ReadBefore = messageRepository.findById(m2.getId()).orElseThrow().getReadAt();
        Instant lastMessageAtBefore = conversationRepository.findById(conv.getId()).orElseThrow().getLastMessageAt();
        assertThat(m1ReadBefore).isNotNull();
        assertThat(m2ReadBefore).isNull();

        // admin performs both reads
        adminConversationService.listConversations(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "lastMessageAt")));
        adminConversationService.getMessages(admin.getId(), conv.getId(), "Auditing", PageRequest.of(0, 20));

        // re-fetch from the DB and assert nothing changed
        assertThat(messageRepository.findById(m1.getId()).orElseThrow().getReadAt()).isEqualTo(m1ReadBefore);
        assertThat(messageRepository.findById(m2.getId()).orElseThrow().getReadAt()).isNull();
        assertThat(conversationRepository.findById(conv.getId()).orElseThrow().getLastMessageAt())
                .isEqualTo(lastMessageAtBefore);
    }

    // --- messageCount correct across a page with multiple conversations (incl. a 0-message one) ---

    @Test
    void messageCount_correctOnMultiConversationPage() {
        User student = persistUser(Role.STUDENT, UserStatus.ACTIVE);
        CoachProfile coach = persistCoach();

        Conversation a = persistConversation(student, coach, Instant.now().minus(Duration.ofMinutes(1)));
        persistMessage(a, student, "1");
        persistMessage(a, student, "2");
        persistMessage(a, coach.getUser(), "3");

        Conversation b = persistConversation(persistUser(Role.STUDENT, UserStatus.ACTIVE), coach,
                Instant.now().minus(Duration.ofMinutes(2)));
        persistMessage(b, b.getStudent(), "only one");

        // a real normal-flow state: a conversation opened but not yet messaged (lastMessageAt set, 0 messages)
        Conversation c = persistConversation(persistUser(Role.STUDENT, UserStatus.ACTIVE), coach,
                Instant.now().minus(Duration.ofMinutes(3)));

        // large page so the seeded conversations are present regardless of rows left by sibling tests
        PageResponse<ConversationSummaryResponse> page =
                adminConversationService.listConversations(PageRequest.of(0, 1000));
        Map<Long, Long> counts = page.content().stream()
                .collect(Collectors.toMap(ConversationSummaryResponse::conversationId,
                        ConversationSummaryResponse::messageCount));

        assertThat(counts.get(a.getId())).isEqualTo(3L);
        assertThat(counts.get(b.getId())).isEqualTo(1L);
        assertThat(counts.get(c.getId())).isEqualTo(0L);
        // participant fields are populated (single fetch, no leak of nulls)
        ConversationSummaryResponse summaryA = page.content().stream()
                .filter(s -> s.conversationId().equals(a.getId())).findFirst().orElseThrow();
        assertThat(summaryA.student().fullName()).isNotBlank();
        assertThat(summaryA.coach().universityName()).isNotBlank();
    }

    // --- N+1 proof: statement count is CONSTANT regardless of how many rows are on the page ---

    @Test
    void list_isNotNPlusOne_statementCountConstantAcrossPageSize() {
        CoachProfile coach = persistCoach();
        for (int i = 0; i < 4; i++) {
            // distinct student per conversation — conversations are UNIQUE(student, coach)
            User student = persistUser(Role.STUDENT, UserStatus.ACTIVE);
            Conversation conv = persistConversation(student, coach, Instant.now().minus(Duration.ofMinutes(i + 1)));
            persistMessage(conv, student, "m" + i);
        }

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        long twoPerPage = countStatements(stats, () -> adminConversationService.listConversations(PageRequest.of(0, 2)));
        long fourPerPage = countStatements(stats, () -> adminConversationService.listConversations(PageRequest.of(0, 4)));

        // data query + Spring count query + exactly ONE messageCount aggregate — independent of
        // how many conversations are on the page. Per-row COUNT would make these differ (it scales
        // with row count: 1+1+2=4 vs 1+1+4=6), even though it would yield identical numbers.
        assertThat(twoPerPage).isEqualTo(fourPerPage);
        assertThat(fourPerPage).isLessThanOrEqualTo(3);
    }

    private long countStatements(Statistics stats, Runnable call) {
        stats.clear();
        call.run();
        return stats.getPrepareStatementCount();
    }

    // --- soft-deleted participants remain visible to the admin (no global filter excludes them) ---

    @Test
    void softDeletedParticipant_stillVisibleToAdmin() {
        User deletedStudent = persistUser(Role.STUDENT, UserStatus.DELETED);
        CoachProfile coach = persistCoach();
        Conversation conv = persistConversation(deletedStudent, coach, Instant.now());
        persistMessage(conv, deletedStudent, "before deletion");

        PageResponse<ConversationSummaryResponse> page =
                adminConversationService.listConversations(PageRequest.of(0, 1000));
        Map<Long, ConversationSummaryResponse> byId = page.content().stream()
                .collect(Collectors.toMap(ConversationSummaryResponse::conversationId, Function.identity()));

        assertThat(byId).containsKey(conv.getId());
        assertThat(byId.get(conv.getId()).student().id()).isEqualTo(deletedStudent.getId());
    }
}
