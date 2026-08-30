package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.ForgotPasswordRequest;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.AccountOrigin;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.validation.EmailAddresses;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CoachAccountProvisioningService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordSecurityService passwordSecurityService;

    public CoachAccountProvisioningService(UserRepository userRepository,
                                           CoachProfileRepository coachProfileRepository,
                                           PasswordEncoder passwordEncoder,
                                           PasswordSecurityService passwordSecurityService) {
        this.userRepository = userRepository;
        this.coachProfileRepository = coachProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordSecurityService = passwordSecurityService;
    }

    @Transactional
    public ProvisionedCoach provision(String fullName, String rawEmail, AccountOrigin origin) {
        if (origin != AccountOrigin.COACH_APPLICATION && origin != AccountOrigin.ADMIN_MANUAL) {
            throw new IllegalArgumentException("Unsupported coach account origin");
        }
        String email = EmailAddresses.normalize(rawEmail);
        if (!EmailAddresses.isValid(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", "Geçerli bir e-posta girin");
        }
        userRepository.findByEmailIgnoreCase(email).ifPresent(existing -> {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS",
                    "Bu e-posta zaten %s rolüyle kayıtlı (kullanıcı #%d)."
                            .formatted(existing.getRole(), existing.getId()));
        });

        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName.trim());
        user.setRole(Role.COACH);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(randomPlaceholder()));
        user.setEmailVerified(true);
        user.setLegalOnboardingCompleted(false);
        user.setAccountOrigin(origin);
        userRepository.save(user);

        CoachProfile profile = new CoachProfile();
        profile.setUser(user);
        profile.setStatus(CoachProfileStatus.PENDING);
        profile.setActiveStudentCount(0);
        profile.setMaxStudentCapacity(10);
        profile.setPayoutAccountReady(false);
        coachProfileRepository.save(profile);

        passwordSecurityService.forgotPassword(new ForgotPasswordRequest(email));
        return new ProvisionedCoach(user, profile);
    }

    private static String randomPlaceholder() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record ProvisionedCoach(User user, CoachProfile profile) { }
}
