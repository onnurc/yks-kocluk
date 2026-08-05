package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.JwtProperties;
import com.ykskocluk.demo.dto.AuthResponse;
import com.ykskocluk.demo.dto.LoginRequest;
import com.ykskocluk.demo.dto.LogoutRequest;
import com.ykskocluk.demo.dto.RefreshRequest;
import com.ykskocluk.demo.dto.RegisterRequest;
import com.ykskocluk.demo.dto.UserResponse;
import com.ykskocluk.demo.entity.RefreshToken;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.entity.LegalDocument;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.UserMapper;
import com.ykskocluk.demo.repository.RefreshTokenRepository;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.repository.AccountDeletionRequestRepository;
import com.ykskocluk.demo.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock UserMapper userMapper;
    @Mock JwtProperties jwtProperties;
    @Mock OAuth2LoginCodeService oauth2LoginCodeService;
    @Mock LegalAcceptanceService legalAcceptanceService;
    @Mock AccountDeletionRequestRepository accountDeletionRequestRepository;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository,
                passwordEncoder, jwtService, userMapper, jwtProperties, oauth2LoginCodeService,
                legalAcceptanceService, accountDeletionRequestRepository);
        // Common stubs for the issueTokens() path; lenient so failure tests don't trip strict stubbing.
        lenient().when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        lenient().when(jwtService.getAccessTtlSeconds()).thenReturn(900L);
        lenient().when(jwtProperties.refreshTtl()).thenReturn(Duration.ofDays(30));
        lenient().when(userMapper.toResponse(any())).thenReturn(
                new UserResponse(1L, "user@example.com", "Test User", Role.STUDENT, UserStatus.ACTIVE));
    }

    private User activeUser(String hash) {
        User u = new User();
        u.setEmail("user@example.com");
        u.setPasswordHash(hash);
        u.setFullName("Test User");
        u.setRole(Role.STUDENT);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    @Test
    void googleLogin_deletedIdentityCannotBeRecreated() {
        when(accountDeletionRequestRepository.existsByIdentityEmailHashAndStatus(anyString(), any()))
                .thenReturn(true);

        ApiException error = catchThrowableOfType(ApiException.class, () ->
                authService.upsertGoogleUser("deleted@example.com", "sub", "Deleted", true));

        assertThat(error.getErrorCode()).isEqualTo("ACCOUNT_DELETED");
        verify(userRepository, never()).save(any());
    }

    // --- register ---

    @Test
    void register_hashesPasswordAndIssuesTokens() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-pw");

        AuthResponse res = authService.register(
                new RegisterRequest("user@example.com", "password123", "Test User", Role.STUDENT, java.time.LocalDate.of(2005, 1, 1)));

        assertThat(res.accessToken()).isEqualTo("access-token");
        assertThat(res.refreshToken()).isNotBlank();
        assertThat(res.tokenType()).isEqualTo("Bearer");
        assertThat(res.expiresIn()).isEqualTo(900L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed-pw");
        assertThat(captor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void register_acceptancePersistenceFailureDoesNotIssueTokens() {
        LegalDocument terms = new LegalDocument();
        LegalDocument explicit = new LegalDocument();
        var documents = new LegalAcceptanceService.RequiredDocuments(terms, explicit);
        when(legalAcceptanceService.validateRequired(3L, 2L)).thenReturn(documents);
        when(userRepository.existsByEmail("atomic@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        doThrow(new RuntimeException("acceptance write failed")).when(legalAcceptanceService)
                .recordRegistrationAcceptances(any(User.class), org.mockito.ArgumentMatchers.same(documents),
                        org.mockito.ArgumentMatchers.eq(false), org.mockito.ArgumentMatchers.eq(false));

        RegisterRequest request = new RegisterRequest("atomic@example.com", "password123", "Atomic User",
                Role.STUDENT, java.time.LocalDate.of(2005, 1, 1), 3L, 2L, false, false);
        assertThatThrownBy(() -> authService.register(request)).hasMessage("acceptance write failed");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        ApiException ex = catchThrowableOfType(ApiException.class, () -> authService.register(
                new RegisterRequest("user@example.com", "password123", "Test User", Role.STUDENT, java.time.LocalDate.of(2005, 1, 1))));
        assertThat(ex.getErrorCode()).isEqualTo("EMAIL_ALREADY_EXISTS");
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_adminRole_rejected() {
        ApiException ex = catchThrowableOfType(ApiException.class, () -> authService.register(
                new RegisterRequest("user@example.com", "password123", "Admin", Role.ADMIN)));
        assertThat(ex.getErrorCode()).isEqualTo("ROLE_NOT_ALLOWED");
    }

    @Test
    void register_studentNullDob_throwsDateOfBirthRequired() {
        RegisterRequest req = new RegisterRequest("student@example.com", "pass1234", "Student", Role.STUDENT, null);
        ApiException ex = catchThrowableOfType(ApiException.class, () -> authService.register(req));
        assertThat(ex.getErrorCode()).isEqualTo("DATE_OF_BIRTH_REQUIRED");
    }

    @Test
    void register_studentFutureDob_throwsInvalidDateOfBirth() {
        java.time.LocalDate futureDob = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Istanbul")).plusDays(1);
        RegisterRequest req = new RegisterRequest("student@example.com", "pass1234", "Student", Role.STUDENT, futureDob);
        ApiException ex = catchThrowableOfType(ApiException.class, () -> authService.register(req));
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_DATE_OF_BIRTH");
    }

    @Test
    void register_studentValidDob_succeeds() {
        java.time.LocalDate dob = java.time.LocalDate.of(2008, 1, 1);
        RegisterRequest req = new RegisterRequest("student@example.com", "pass1234", "Student", Role.STUDENT, dob);
        when(userRepository.existsByEmail(req.email())).thenReturn(false);
        when(passwordEncoder.encode(req.password())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(req);
    }

    @Test
    void register_coachNullDob_succeeds() {
        RegisterRequest req = new RegisterRequest("coach@example.com", "pass1234", "Coach", Role.COACH, null);
        when(userRepository.existsByEmail(req.email())).thenReturn(false);
        when(passwordEncoder.encode(req.password())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(req);
    }

    // --- login ---

    @Test
    void login_success() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser("hashed-pw")));
        when(passwordEncoder.matches("password123", "hashed-pw")).thenReturn(true);

        AuthResponse res = authService.login(new LoginRequest("user@example.com", "password123"));
        assertThat(res.accessToken()).isEqualTo("access-token");
    }

    @Test
    void login_wrongPassword_throws401() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser("hashed-pw")));
        when(passwordEncoder.matches("bad", "hashed-pw")).thenReturn(false);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> authService.login(new LoginRequest("user@example.com", "bad")));
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void login_googleOnlyAccount_throws401() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser(null)));

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> authService.login(new LoginRequest("user@example.com", "whatever")));
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void login_suspendedAccount_throws403() {
        User u = activeUser("hashed-pw");
        u.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("password123", "hashed-pw")).thenReturn(true);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> authService.login(new LoginRequest("user@example.com", "password123")));
        assertThat(ex.getErrorCode()).isEqualTo("ACCOUNT_NOT_ACTIVE");
    }

    // --- refresh rotation ---

    @Test
    void refresh_rotatesToken_revokesOldIssuesNew() {
        RefreshToken stored = new RefreshToken();
        stored.setUser(activeUser("hashed-pw"));
        stored.setTokenHash("hash");
        stored.setExpiresAt(Instant.now().plus(Duration.ofDays(10)));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        AuthResponse res = authService.refresh(new RefreshRequest("raw-refresh"));

        assertThat(stored.getRevokedAt()).isNotNull(); // old token revoked
        assertThat(res.accessToken()).isEqualTo("access-token");
        verify(refreshTokenRepository).save(any(RefreshToken.class)); // new token persisted
    }

    @Test
    void refresh_revokedToken_throws401() {
        RefreshToken revoked = new RefreshToken();
        revoked.setUser(activeUser("hashed-pw"));
        revoked.setExpiresAt(Instant.now().plus(Duration.ofDays(10)));
        revoked.setRevokedAt(Instant.now());
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> authService.refresh(new RefreshRequest("raw-refresh")));
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void refresh_unknownToken_throws401() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("nope")))
                .isInstanceOf(ApiException.class);
    }

    // --- logout ---

    @Test
    void logout_revokesToken() {
        RefreshToken stored = new RefreshToken();
        stored.setExpiresAt(Instant.now().plus(Duration.ofDays(10)));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        authService.logout(new LogoutRequest("raw-refresh"));
        assertThat(stored.getRevokedAt()).isNotNull();
    }

    // --- Google account linking ---

    @Test
    void upsertGoogleUser_linksByVerifiedEmail() {
        User existing = activeUser("hashed-pw");
        when(userRepository.findByGoogleSub("sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));

        authService.upsertGoogleUser("user@example.com", "sub-123", "Test User", true);

        assertThat(existing.getGoogleSub()).isEqualTo("sub-123");
        assertThat(existing.isEmailVerified()).isTrue();
        verify(userRepository, never()).save(any()); // existing row updated in place
    }

    @Test
    void upsertGoogleUser_newUser_createsStudent() {
        when(userRepository.findByGoogleSub("sub-999")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        authService.upsertGoogleUser("new@example.com", "sub-999", "New User", true);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.STUDENT);
        assertThat(captor.getValue().getGoogleSub()).isEqualTo("sub-999");
        assertThat(captor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(captor.getValue().isLegalOnboardingCompleted()).isFalse();
    }

    @Test
    void upsertGoogleUser_unverifiedGoogleEmail_throwsUnauthorized() {
        assertThatThrownBy(() -> authService.upsertGoogleUser("unverified@example.com", "sub-111", "Unverified", false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("E-posta adresi doğrulanmamış");
    }

    @Test
    void upsertGoogleUser_conflictingGoogleSub_throwsUnauthorized() {
        User existing = activeUser("hashed-pw");
        existing.setGoogleSub("sub-original");
        when(userRepository.findByGoogleSub("sub-hacker")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.upsertGoogleUser("user@example.com", "sub-hacker", "Hacker", true))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("E-posta veya şifre hatalı");
    }

    @Test
    void upsertGoogleUser_suspendedUser_throwsAccountNotActive() {
        User existing = activeUser("hashed-pw");
        existing.setGoogleSub("sub-123");
        existing.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByGoogleSub("sub-123")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.upsertGoogleUser("user@example.com", "sub-123", "Suspended", true))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Hesabınız aktif değil");
    }

    @Test
    void upsertGoogleUser_existingCoach_preservesRoleAndStatus() {
        User existing = activeUser("hashed-pw");
        existing.setRole(Role.COACH);
        existing.setStatus(UserStatus.ACTIVE);
        when(userRepository.findByGoogleSub("sub-coach")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("coach@example.com")).thenReturn(Optional.of(existing));

        User linked = authService.upsertGoogleUser("coach@example.com", "sub-coach", "Coach User", true);

        assertThat(linked.getRole()).isEqualTo(Role.COACH);
        assertThat(linked.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(linked.getGoogleSub()).isEqualTo("sub-coach");
    }

    @Test
    void exchangeOAuth2Code_validCode_issuesTokens() {
        User user = activeUser("hashed-pw");
        when(oauth2LoginCodeService.consumeCode("valid-code")).thenReturn(user);

        AuthResponse response = authService.exchangeOAuth2Code("valid-code");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        verify(oauth2LoginCodeService).consumeCode("valid-code");
    }
}
