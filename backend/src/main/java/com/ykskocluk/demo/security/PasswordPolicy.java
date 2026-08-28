package com.ykskocluk.demo.security;

import com.ykskocluk.demo.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.Set;

/** Shared password policy for registration, reset and password change. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 72;

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "password123", "123456789012", "qwerty123456",
            "letmein123456", "adminadmin12", "changeme1234", "iloveyou1234");

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            throw violation("Şifre 12 ile 72 karakter arasında olmalı");
        }
        String normalized = rawPassword.strip().toLowerCase(Locale.ROOT);
        if (COMMON_PASSWORDS.contains(normalized)) {
            throw violation("Bu şifre çok yaygın olduğu için kullanılamaz");
        }
    }

    private static ApiException violation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY_VIOLATION", message);
    }
}
