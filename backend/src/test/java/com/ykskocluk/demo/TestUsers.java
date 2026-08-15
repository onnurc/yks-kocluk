package com.ykskocluk.demo;

import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.repository.UserRepository;

/**
 * Single place to build a "ready" {@link User} for Testcontainers integration tests — ACTIVE,
 * {@code emailVerified = true}, {@code legalOnboardingCompleted = true}. Direct-repository test
 * fixtures used to build users without those two flags, which worked fine until
 * {@code AccountReadinessService.requireReady} started gating booking/checkout/messaging on them
 * (V23, email verification); every fixture that predated that gate then silently started failing
 * with {@code EMAIL_VERIFICATION_REQUIRED} the moment it exercised a gated code path. Route every
 * direct-repository user fixture through here instead of constructing {@link User} inline, so a
 * future readiness precondition only needs updating in one place.
 */
final class TestUsers {

    private TestUsers() {
    }

    static User create(UserRepository repo, Role role) {
        return create(repo, role, role.name().toLowerCase() + "-" + System.nanoTime() + "@example.com");
    }

    static User create(UserRepository repo, Role role, String email) {
        return create(repo, role, email, role.name() + " User");
    }

    static User create(UserRepository repo, Role role, String email, String fullName) {
        return create(repo, role, email, fullName, UserStatus.ACTIVE);
    }

    static User create(UserRepository repo, Role role, String email, String fullName, UserStatus status) {
        User u = new User();
        u.setEmail(email);
        u.setFullName(fullName);
        u.setRole(role);
        u.setStatus(status);
        u.setEmailVerified(true);
        u.setLegalOnboardingCompleted(true);
        return repo.save(u);
    }

    /**
     * Same drift, different door: tests that register through the real
     * {@code POST /api/v1/auth/register} endpoint (rather than a direct repository fixture) get a
     * real, unverified account — {@code AuthService.register} always sets
     * {@code emailVerified = false}, since that's what the endpoint is supposed to do. Call this
     * right after registering to flip it, the same way {@link #create} does for direct fixtures.
     */
    static void verifyEmail(UserRepository repo, String email) {
        User u = repo.findByEmail(email).orElseThrow();
        u.setEmailVerified(true);
        repo.save(u);
    }
}
