package com.ykskocluk.demo.security;

import com.ykskocluk.demo.enums.Role;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;

/** Authentication plus the immutable JWT state that must remain valid for a STOMP session. */
public final class StompSessionAuthentication extends UsernamePasswordAuthenticationToken {
    private final Instant tokenIssuedAt;
    private final Instant tokenExpiresAt;
    private final int passwordVersion;
    private final Role role;

    public StompSessionAuthentication(Long userId, Instant tokenIssuedAt, Instant tokenExpiresAt,
                                      int passwordVersion, Role role) {
        super(userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        this.tokenIssuedAt = tokenIssuedAt;
        this.tokenExpiresAt = tokenExpiresAt;
        this.passwordVersion = passwordVersion;
        this.role = role;
    }

    public Long userId() { return (Long) getPrincipal(); }
    public Instant tokenIssuedAt() { return tokenIssuedAt; }
    public Instant tokenExpiresAt() { return tokenExpiresAt; }
    public int passwordVersion() { return passwordVersion; }
    public Role role() { return role; }
}
