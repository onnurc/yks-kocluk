package com.ykskocluk.demo;

import com.ykskocluk.demo.config.JwtProperties;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Conversation;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.integration.MailClient;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.ConversationRepository;
import com.ykskocluk.demo.repository.PackageRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UniversityRepository;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Message fan-out end to end, over a real broker and a real HTTP port.
 *
 * <p>Two things are being protected here. First, that moving the broadcast out of
 * {@code ChatStompController} into the after-commit listener delivers each message
 * <strong>exactly once</strong> — the obvious failure mode of that refactor is broadcasting from
 * both places. Second, that the REST send path (the client's fallback whenever the socket is down)
 * now reaches subscribers at all, which it never did before.
 *
 * <p>Not {@code @Transactional}: the whole mechanism hangs off AFTER_COMMIT, which a
 * rolled-back test transaction would never trigger.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class MessageNotificationIntegrationTest {

    /** Quiet period used to prove a *second* frame never arrives. */
    private static final int NO_MORE_FRAMES_MS = 1500;

    @LocalServerPort int port;

    @Autowired JwtService jwtService;
    @Autowired JwtProperties jwtProperties;
    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired ConversationRepository conversationRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired PackageRepository packageRepository;

    /** Replaces StubMailClient so the offline-email decision can be asserted. */
    @MockitoBean MailClient mailClient;

    private WebSocketStompClient stompClient;
    private final RestTemplate restTemplate = new RestTemplate();

    private User student;
    private User coachUser;
    private long conversationId;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new StringMessageConverter());
        stompClient.setTaskScheduler(new ConcurrentTaskScheduler());

        University uni = new University();
        uni.setName("Notify Uni " + System.nanoTime());
        universityRepository.save(uni);

        coachUser = TestUsers.create(userRepository, Role.COACH);
        CoachProfile coach = new CoachProfile();
        coach.setUser(coachUser);
        coach.setUniversity(uni);
        coach.setHeadline("Koç");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(5);
        coach.setActiveStudentCount(0);
        coachProfileRepository.save(coach);

        student = TestUsers.create(userRepository, Role.STUDENT);

        // The gate applies here too: sending requires an ACTIVE subscription.
        Subscription subscription = new Subscription();
        subscription.setStudent(student);
        subscription.setCoachProfile(coach);
        subscription.setPkg(packageRepository.findByActiveTrueOrderByPriceAsc().get(0));
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartAt(Instant.now());
        subscription.setEndAt(Instant.now().plus(30, ChronoUnit.DAYS));
        subscriptionRepository.save(subscription);

        Conversation conversation = new Conversation();
        conversation.setStudent(student);
        conversation.setCoachProfile(coach);
        conversation.setLastMessageAt(Instant.now());
        conversationId = conversationRepository.save(conversation).getId();
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    @Test
    void socketSentMessage_reachesTheTopicExactlyOnce() throws Exception {
        StompSession coachSession = connect(jwtService.generateAccessToken(coachUser));
        BlockingQueue<String> topicFrames = new LinkedBlockingQueue<>();
        coachSession.subscribe("/topic/conversations/" + conversationId, frameHandler(topicFrames));
        settle();

        StompSession studentSession = connect(jwtService.generateAccessToken(student));
        studentSession.send("/app/conversations/" + conversationId + "/send", "ws mesaji");

        assertThat(topicFrames.poll(5, TimeUnit.SECONDS)).contains("ws mesaji");
        // The regression this test exists for: the controller broadcasting *and* the listener
        // broadcasting would put a second identical frame here.
        assertThat(topicFrames.poll(NO_MORE_FRAMES_MS, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void restSentMessage_alsoReachesTheTopicExactlyOnce() throws Exception {
        StompSession coachSession = connect(jwtService.generateAccessToken(coachUser));
        BlockingQueue<String> topicFrames = new LinkedBlockingQueue<>();
        coachSession.subscribe("/topic/conversations/" + conversationId, frameHandler(topicFrames));
        settle();

        // Before the fan-out moved after commit, this delivered nothing at all.
        postMessageOverRest("rest mesaji");

        assertThat(topicFrames.poll(5, TimeUnit.SECONDS)).contains("rest mesaji");
        assertThat(topicFrames.poll(NO_MORE_FRAMES_MS, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void recipientOnTheUserQueue_getsTheUnreadTotal_withoutMessageText() throws Exception {
        StompSession coachSession = connect(jwtService.generateAccessToken(coachUser));
        BlockingQueue<String> notifications = new LinkedBlockingQueue<>();
        coachSession.subscribe("/user/queue/notifications", frameHandler(notifications));
        settle();

        postMessageOverRest("cok gizli icerik");

        String payload = notifications.poll(5, TimeUnit.SECONDS);
        assertThat(payload).isNotNull();
        assertThat(payload).contains("NEW_MESSAGE").contains("\"unreadTotal\":1");
        // The badge channel carries counts, not conversation content.
        assertThat(payload).doesNotContain("cok gizli icerik");
        assertThat(notifications.poll(NO_MORE_FRAMES_MS, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void connectedRecipient_getsNoEmail() throws Exception {
        // Coach is on the socket, so SimpUserRegistry sees them and the mail is suppressed.
        connect(jwtService.generateAccessToken(coachUser));
        settle();

        postMessageOverRest("merhaba");

        Thread.sleep(NO_MORE_FRAMES_MS);
        verify(mailClient, org.mockito.Mockito.never())
                .sendNewMessageNotification(any(), any(), any());
    }

    @Test
    void offlineRecipient_getsOneEmail_andNoSecondOneInsideTheWindow() {
        // Nobody connects as the coach at all — the offline path.
        postMessageOverRest("ilk mesaj");
        postMessageOverRest("ikinci mesaj");
        postMessageOverRest("ucuncu mesaj");

        // Three messages, one email: the debounce claim refused the other two.
        verify(mailClient, timeout(5000).times(1))
                .sendNewMessageNotification(eq(coachUser.getEmail()), any(), any());
    }

    @Test
    void rawQueueSubscribe_isRejected() throws Exception {
        RecordingSessionHandler handler = new RecordingSessionHandler();
        StompSession session = stompClient.connectAsync("ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(), authHeaders(jwtService.generateAccessToken(coachUser)),
                handler).get(5, TimeUnit.SECONDS);

        // Only the /user prefix resolves to the caller's own session; the broker-side destination
        // is off limits.
        session.subscribe("/queue/notifications-userSOMEONEELSE", frameHandler(new LinkedBlockingQueue<>()));

        assertThat(handler.errorLatch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    // --- helpers ---

    private void postMessageOverRest(String content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwtService.generateAccessToken(student));
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/conversations/" + conversationId + "/messages",
                new HttpEntity<>("{\"content\":\"" + content + "\"}", headers), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private StompSession connect(String token) throws Exception {
        return stompClient.connectAsync("ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(), authHeaders(token),
                new StompSessionHandlerAdapter() {
                }).get(5, TimeUnit.SECONDS);
    }

    private StompHeaders authHeaders(String token) {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        return connectHeaders;
    }

    /**
     * subscribe() returns once the frame is written, not once the server has registered it, and
     * the in-memory broker sends no RECEIPT to wait on (see WebSocketAuthTest) — so give the
     * SUBSCRIBE a beat before triggering the send it is meant to catch.
     */
    private void settle() throws InterruptedException {
        Thread.sleep(400);
    }

    private StompFrameHandler frameHandler(BlockingQueue<String> sink) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                sink.add((String) payload);
            }
        };
    }

    private static class RecordingSessionHandler extends StompSessionHandlerAdapter {
        final CountDownLatch errorLatch = new CountDownLatch(1);

        @Override
        public void handleException(StompSession session, StompCommand command,
                                    StompHeaders headers, byte[] payload, Throwable exception) {
            errorLatch.countDown();
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            errorLatch.countDown();
        }
    }
}
