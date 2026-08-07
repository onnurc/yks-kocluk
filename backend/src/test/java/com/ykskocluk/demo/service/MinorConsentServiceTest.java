package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.ConsentCreateRequest;
import com.ykskocluk.demo.dto.ConsentStatusResponse;
import com.ykskocluk.demo.dto.RegisterRequest;
import com.ykskocluk.demo.entity.ConsentRecord;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.ConsentStatus;
import com.ykskocluk.demo.enums.ConsentType;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.ConsentRecordRepository;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.repository.RefreshTokenRepository;
import com.ykskocluk.demo.repository.AccountDeletionRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinorConsentServiceTest {

    @Mock private ConsentRecordRepository consentRecordRepository;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private com.ykskocluk.demo.security.JwtService jwtService;
    @Mock private com.ykskocluk.demo.mapper.UserMapper userMapper;
    @Mock private com.ykskocluk.demo.config.JwtProperties jwtProperties;
    @Mock private OAuth2LoginCodeService oauth2LoginCodeService;
    @Mock private LegalAcceptanceService legalAcceptanceService;
    @Mock private AccountDeletionRequestRepository accountDeletionRequestRepository;
    @Mock private EmailVerificationService emailVerificationService;

    @InjectMocks private ConsentService consentService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtService,
                userMapper, jwtProperties, oauth2LoginCodeService, legalAcceptanceService,
                accountDeletionRequestRepository, emailVerificationService);
        lenient().when(jwtService.generateAccessToken(any())).thenReturn("dummy-access");
        lenient().when(jwtProperties.refreshTtl()).thenReturn(java.time.Duration.ofDays(30));
        lenient().when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // --- Age calculation tests ---

    @Test
    void isMinor_exactly18YearsOld_isAdult() {
        LocalDate dob = LocalDate.now(ZoneId.of("Europe/Istanbul")).minusYears(18);
        assertThat(consentService.isMinor(dob)).isFalse();
    }

    @Test
    void isMinor_oneDayShortOf18_isMinor() {
        LocalDate dob = LocalDate.now(ZoneId.of("Europe/Istanbul")).minusYears(18).plusDays(1);
        assertThat(consentService.isMinor(dob)).isTrue();
    }

    @Test
    void isMinor_olderThan18_isAdult() {
        LocalDate dob = LocalDate.now(ZoneId.of("Europe/Istanbul")).minusYears(25);
        assertThat(consentService.isMinor(dob)).isFalse();
    }

    @Test
    void isMinor_nullDateOfBirth_isAdultForLegacyCompatibility() {
        assertThat(consentService.isMinor(null)).isFalse();
    }

    // --- Registration validation tests ---

    @Test
    void register_studentNullDob_throwsDateOfBirthRequired() {
        RegisterRequest req = new RegisterRequest("student@example.com", "pass1234", "Student", Role.STUDENT, null);
        
        ApiException ex = catchThrowableOfType(
                ApiException.class,
                () -> authService.register(req)
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getErrorCode()).isEqualTo("DATE_OF_BIRTH_REQUIRED");
    }

    @Test
    void register_studentFutureDob_throwsInvalidDateOfBirth() {
        LocalDate futureDob = LocalDate.now(ZoneId.of("Europe/Istanbul")).plusDays(1);
        RegisterRequest req = new RegisterRequest("student@example.com", "pass1234", "Student", Role.STUDENT, futureDob);

        ApiException ex = catchThrowableOfType(
                ApiException.class,
                () -> authService.register(req)
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_DATE_OF_BIRTH");
    }

    @Test
    void register_studentValidDob_succeeds() {
        LocalDate dob = LocalDate.of(2008, 1, 1);
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

    // --- Consent enforcement gate tests ---

    @Test
    void checkConsentRequired_adultStudent_succeeds() {
        User user = new User();
        user.setRole(Role.STUDENT);
        user.setDateOfBirth(LocalDate.now(ZoneId.of("Europe/Istanbul")).minusYears(19));

        consentService.checkConsentRequiredForAction(user); // should not throw
    }

    @Test
    void checkConsentRequired_minorStudentNoLegacyRecord_usesGeneralOnboardingGate() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 42L);
        user.setRole(Role.STUDENT);
        user.setDateOfBirth(LocalDate.now(ZoneId.of("Europe/Istanbul")).minusYears(17));

        consentService.checkConsentRequiredForAction(user);
        org.mockito.Mockito.verify(legalAcceptanceService).requireCompleted(user);
    }

    @Test
    void checkConsentRequired_minorStudentAccepted_succeeds() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 42L);
        user.setRole(Role.STUDENT);
        user.setDateOfBirth(LocalDate.now(ZoneId.of("Europe/Istanbul")).minusYears(17));

        consentService.checkConsentRequiredForAction(user);
        org.mockito.Mockito.verify(legalAcceptanceService).requireCompleted(user);
    }

    @Test
    void checkConsentRequired_minorStudentRevokedLegacyRecord_doesNotCreateSeparateGate() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 42L);
        user.setRole(Role.STUDENT);
        user.setDateOfBirth(LocalDate.now(ZoneId.of("Europe/Istanbul")).minusYears(17));

        consentService.checkConsentRequiredForAction(user);
        org.mockito.Mockito.verify(legalAcceptanceService).requireCompleted(user);
    }

    @Test
    void getConsentStatus_outdatedVersion_returnsPendingStatus() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 42L);

        ConsentRecord oldRecord = new ConsentRecord();
        oldRecord.setUser(user);
        oldRecord.setConsentType(ConsentType.KVKK);
        oldRecord.setDocumentVersion("v0.9");
        oldRecord.setStatus(ConsentStatus.ACCEPTED);

        when(consentRecordRepository.findFirstByUserIdAndConsentTypeOrderByAcceptedAtDesc(42L, ConsentType.KVKK))
                .thenReturn(Optional.of(oldRecord));

        ConsentStatusResponse response = consentService.getConsentStatus(42L, ConsentType.KVKK);
        assertThat(response.hasConsented()).isFalse();
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void getConsentStatus_revoked_returnsRevokedStatus() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 42L);

        ConsentRecord record = new ConsentRecord();
        record.setUser(user);
        record.setConsentType(ConsentType.KVKK);
        record.setDocumentVersion(ConsentService.CURRENT_KVKK_VERSION);
        record.setStatus(ConsentStatus.REVOKED);

        when(consentRecordRepository.findFirstByUserIdAndConsentTypeOrderByAcceptedAtDesc(42L, ConsentType.KVKK))
                .thenReturn(Optional.of(record));

        ConsentStatusResponse response = consentService.getConsentStatus(42L, ConsentType.KVKK);
        assertThat(response.hasConsented()).isFalse();
        assertThat(response.status()).isEqualTo("REVOKED");
    }
}
