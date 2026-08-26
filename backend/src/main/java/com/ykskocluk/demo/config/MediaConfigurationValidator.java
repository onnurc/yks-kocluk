package com.ykskocluk.demo.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.net.URI;

public class MediaConfigurationValidator {
    private final R2Properties r2;
    private final MediaPublicUrlProperties media;
    private final Environment environment;

    public MediaConfigurationValidator(R2Properties r2, MediaPublicUrlProperties media, Environment environment) {
        this.r2 = r2;
        this.media = media;
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        if (!r2.enabled() || environment.acceptsProfiles(Profiles.of("local"))) return;
        String value = media.publicBaseUrl();
        if (value == null) {
            throw new IllegalStateException("MEDIA_PUBLIC_BASE_URL is required when R2 is enabled outside local development");
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("MEDIA_PUBLIC_BASE_URL must be a valid absolute HTTPS origin", ex);
        }
        String host = uri.getHost();
        boolean invalidHost = host == null || host.equalsIgnoreCase("localhost")
                || host.equals("127.0.0.1") || host.equals("::1") || host.equals("0.0.0.0");
        boolean hasUnexpectedParts = uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isBlank() && !uri.getPath().equals("/"));
        if (!"https".equalsIgnoreCase(uri.getScheme()) || invalidHost || hasUnexpectedParts) {
            throw new IllegalStateException("MEDIA_PUBLIC_BASE_URL must be a non-local absolute HTTPS origin when R2 is enabled outside local development");
        }
    }
}
