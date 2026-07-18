package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.OAuth2LoginCode;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.OAuth2LoginCodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class OAuth2LoginCodeService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OAuth2LoginCodeRepository oauth2LoginCodeRepository;
    private final Clock clock;
    private final Duration codeTtl;

    public OAuth2LoginCodeService(OAuth2LoginCodeRepository oauth2LoginCodeRepository,
                                  Clock clock,
                                  @Value("${app.oauth2.login-code-ttl-seconds:120}") long codeTtlSeconds) {
        this.oauth2LoginCodeRepository = oauth2LoginCodeRepository;
        this.clock = clock;
        this.codeTtl = Duration.ofSeconds(codeTtlSeconds);
    }

    @Transactional
    public String generateCodeForUser(User user) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String rawCode = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        OAuth2LoginCode code = new OAuth2LoginCode();
        code.setUser(user);
        code.setCodeHash(sha256Hex(rawCode));
        code.setExpiresAt(Instant.now(clock).plus(codeTtl));
        oauth2LoginCodeRepository.save(code);

        return rawCode;
    }

    @Transactional
    public User consumeCode(String rawCode) {
        String hash = sha256Hex(rawCode);
        OAuth2LoginCode code = oauth2LoginCodeRepository.findByCodeHash(hash)
                .orElseThrow(this::invalidCodeException);

        int updated = oauth2LoginCodeRepository.consumeCodeAtomically(hash, Instant.now(clock));
        if (updated != 1) {
            throw invalidCodeException();
        }

        return code.getUser();
    }

    private ApiException invalidCodeException() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_OAUTH_CODE",
                "Geçersiz veya süresi dolmuş kod");
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
