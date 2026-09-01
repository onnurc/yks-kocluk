package com.ykskocluk.demo.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/** Fails non-development startup before auth links or redirects can target a local/untrusted origin. */
@Component
public class FrontendUrlProductionConfigurationValidator implements SmartInitializingSingleton {
    private static final Set<String> NON_PRODUCTION_PROFILES = Set.of("local", "test", "stub");

    private final PasswordSecurityProperties passwordProperties;
    private final MessageNotificationProperties notificationProperties;
    private final String oauthRedirectUri;
    private final Environment environment;

    public FrontendUrlProductionConfigurationValidator(
            PasswordSecurityProperties passwordProperties,
            MessageNotificationProperties notificationProperties,
            @Value("${app.oauth2.frontend-redirect-uri}") String oauthRedirectUri,
            Environment environment) {
        this.passwordProperties = passwordProperties;
        this.notificationProperties = notificationProperties;
        this.oauthRedirectUri = oauthRedirectUri;
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validate();
    }

    void validate() {
        if (hasExplicitNonProductionProfile()) return;

        URI passwordBase = validatePublicHttps(passwordProperties.frontendBaseUrl(),
                "FRONTEND_BASE_URL for password reset", true);
        URI notificationBase = validatePublicHttps(notificationProperties.frontendBaseUrl(),
                "FRONTEND_BASE_URL for message notifications", true);
        URI oauthRedirect = validatePublicHttps(oauthRedirectUri,
                "OAUTH2_FRONTEND_REDIRECT_URI", false);

        String expectedOrigin = origin(passwordBase);
        if (!expectedOrigin.equals(origin(notificationBase)) || !expectedOrigin.equals(origin(oauthRedirect))) {
            throw new IllegalStateException("Security-sensitive frontend URLs must use the same approved origin");
        }
    }

    private boolean hasExplicitNonProductionProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if (NON_PRODUCTION_PROFILES.contains(profile.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static URI validatePublicHttps(String value, String label, boolean originOnly) {
        URI uri;
        try {
            uri = URI.create(value == null ? "" : value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(label + " must be a valid absolute HTTPS URL", exception);
        }
        String host = uri.getHost();
        if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443) || isLocalOrPrivateHost(host)) {
            throw new IllegalStateException(label + " must be an explicit public HTTPS URL");
        }
        String path = uri.getPath();
        if (originOnly && path != null && !path.isBlank() && !"/".equals(path)) {
            throw new IllegalStateException(label + " must be an HTTPS origin without a path");
        }
        return uri;
    }

    private static boolean isLocalOrPrivateHost(String rawHost) {
        String host = rawHost.toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
                || host.endsWith(".internal")) return true;
        if (!isIpLiteral(host)) return false;
        try {
            InetAddress address = InetAddress.getByName(host);
            byte[] bytes = address.getAddress();
            boolean carrierGradeNat = bytes.length == 4 && Byte.toUnsignedInt(bytes[0]) == 100
                    && (Byte.toUnsignedInt(bytes[1]) & 0b1100_0000) == 64;
            return address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress() || carrierGradeNat;
        } catch (UnknownHostException exception) {
            return true;
        }
    }

    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0
                || host.chars().allMatch(character -> Character.isDigit(character) || character == '.');
    }

    private static String origin(URI uri) {
        return "https://" + uri.getHost().toLowerCase(Locale.ROOT);
    }
}
