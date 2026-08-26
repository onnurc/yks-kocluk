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
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.enums.AccountDeletionStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.UserMapper;
import com.ykskocluk.demo.repository.RefreshTokenRepository;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.repository.AccountDeletionRequestRepository;
import com.ykskocluk.demo.security.JwtService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final JwtProperties jwtProperties;
    private final OAuth2LoginCodeService oauth2LoginCodeService;
    private final LegalAcceptanceService legalAcceptanceService;
    private final AccountDeletionRequestRepository accountDeletionRequestRepository;
    private final EmailVerificationService emailVerificationService;
    private final StudentProfileProvisioningService studentProfileProvisioningService;
    private final ApplicationEventPublisher eventPublisher;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       UserMapper userMapper,
                       JwtProperties jwtProperties,
                       OAuth2LoginCodeService oauth2LoginCodeService,
                       LegalAcceptanceService legalAcceptanceService,
                       AccountDeletionRequestRepository accountDeletionRequestRepository,
                       EmailVerificationService emailVerificationService,
                       StudentProfileProvisioningService studentProfileProvisioningService,
                       ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.jwtProperties = jwtProperties;
        this.oauth2LoginCodeService = oauth2LoginCodeService;
        this.legalAcceptanceService = legalAcceptanceService;
        this.accountDeletionRequestRepository = accountDeletionRequestRepository;
        this.emailVerificationService = emailVerificationService;
        this.studentProfileProvisioningService = studentProfileProvisioningService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        ensureIdentityWasNotDeleted(request.email(), null);
        if (request.role() == Role.ADMIN || request.role() == Role.COACH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_NOT_ALLOWED",
                    "Bu rol ile kayıt olunamaz");
        }
        if (request.role() == Role.STUDENT && request.dateOfBirth() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_OF_BIRTH_REQUIRED",
                    "Öğrenci kaydı için doğum tarihi zorunludur");
        }
        if (request.dateOfBirth() != null) {
            java.time.LocalDate now = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Istanbul"));
            if (request.dateOfBirth().isAfter(now)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_OF_BIRTH",
                        "Doğum tarihi gelecekte olamaz");
            }
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS",
                    "Bu e-posta zaten kayıtlı");
        }
        LegalAcceptanceService.RequiredDocuments requiredDocuments = legalAcceptanceService.validateRequired(
                request.acceptedTermsDocumentId(), request.acceptedExplicitConsentDocumentId());
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setRole(request.role());
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(false);
        if (request.role() == Role.STUDENT) {
            user.setDateOfBirth(request.dateOfBirth());
        }
        userRepository.save(user);
        legalAcceptanceService.recordRegistrationAcceptances(user, requiredDocuments,
                Boolean.TRUE.equals(request.marketingEmailOptIn()), Boolean.TRUE.equals(request.marketingSmsOptIn()));
        emailVerificationService.issueForRegistration(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(this::invalidCredentials);
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        ensureActive(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(sha256Hex(request.refreshToken()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                        "INVALID_REFRESH_TOKEN", "Geçersiz yenileme jetonu"));
        if (!token.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                    "Yenileme jetonu geçersiz veya süresi dolmuş");
        }
        User user = token.getUser();
        ensureActive(user);
        token.setRevokedAt(Instant.now()); // rotation: revoke the presented token
        return issueTokens(user);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        // Idempotent: revoke if present, no-op otherwise.
        refreshTokenRepository.findByTokenHash(sha256Hex(request.refreshToken()))
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND", "Kullanıcı bulunamadı"));
        return userMapper.toResponse(user);
    }

    /**
     * Google sign-in. Resolution order: by googleSub → by verified email (auto-link the
     * sub onto the existing account) → create a new STUDENT account. Returns the User.
     */
    @Transactional
    public User upsertGoogleUser(String email, String googleSub,
                                 String fullName, boolean emailVerified) {
        if (!emailVerified) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "E-posta adresi doğrulanmamış");
        }
        ensureIdentityWasNotDeleted(email, googleSub);

        Optional<User> bySub = userRepository.findByGoogleSub(googleSub);
        User user;
        if (bySub.isPresent()) {
            user = bySub.get();
            if (!user.isEmailVerified()) {
                user.setEmailVerified(true);
                emailVerificationService.invalidateOutstanding(user);
                eventPublisher.publishEvent(new WelcomeMailEvent(user.getEmail(), user.getFullName()));
            }
        } else {
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                user = byEmail.get();
                if (user.getGoogleSub() != null && !user.getGoogleSub().equals(googleSub)) {
                    throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                            "E-posta veya şifre hatalı");
                }
                user.setGoogleSub(googleSub);
                if (!user.isEmailVerified()) {
                    user.setEmailVerified(true);
                    emailVerificationService.invalidateOutstanding(user);
                    eventPublisher.publishEvent(new WelcomeMailEvent(user.getEmail(), user.getFullName()));
                }
            } else {
                user = new User();
                user.setEmail(email);
                user.setFullName(fullName != null ? fullName : email);
                user.setRole(Role.STUDENT); // new Google sign-ups default to STUDENT
                user.setStatus(UserStatus.ACTIVE);
                user.setGoogleSub(googleSub);
                user.setEmailVerified(true);
                user.setLegalOnboardingCompleted(false);
                userRepository.save(user);
                eventPublisher.publishEvent(new WelcomeMailEvent(user.getEmail(), user.getFullName()));
            }
        }
        ensureActive(user);
        studentProfileProvisioningService.ensureForStudent(user);
        return user;
    }

    @Transactional
    public AuthResponse exchangeOAuth2Code(String rawCode) {
        User user = oauth2LoginCodeService.consumeCode(rawCode);
        ensureActive(user);
        return issueTokens(user);
    }

    // --- helpers ---

    private AuthResponse issueTokens(User user) {
        studentProfileProvisioningService.ensureForStudent(user);
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefresh = generateRawRefreshToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(sha256Hex(rawRefresh));
        refreshToken.setExpiresAt(Instant.now().plus(jwtProperties.refreshTtl()));
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(accessToken, rawRefresh, "Bearer",
                jwtService.getAccessTtlSeconds(), userMapper.toResponse(user));
    }

    private void ensureActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE",
                    "Hesabınız aktif değil");
        }
    }

    private void ensureIdentityWasNotDeleted(String email, String googleSub) {
        boolean deletedEmail = accountDeletionRequestRepository.existsByIdentityEmailHashAndStatus(
                AccountDeletionService.identityHash(email), AccountDeletionStatus.COMPLETED);
        boolean deletedSubject = googleSub != null
                && accountDeletionRequestRepository.existsByOauthSubjectHashAndStatus(
                        AccountDeletionService.identityHash(googleSub), AccountDeletionStatus.COMPLETED);
        if (deletedEmail || deletedSubject) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DELETED",
                    "Silinmiş hesap kimliği yeniden kullanılamaz");
        }
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "E-posta veya şifre hatalı");
    }

    private static String generateRawRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
