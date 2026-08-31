package com.ykskocluk.demo.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/** Fails startup before a real payment client can use an unsafe provider or callback origin. */
@Component
public class IyzicoProductionConfigurationValidator implements SmartInitializingSingleton {

    private static final Set<String> NON_PRODUCTION_PROFILES = Set.of("local", "test", "stub");
    private static final Set<String> APPROVED_PROVIDER_HOSTS = Set.of(
            "api.iyzipay.com", "sandbox-api.iyzipay.com",
            "api.iyzico.com", "sandbox-api.iyzico.com");

    private final IyzicoProperties properties;
    private final Environment environment;

    public IyzicoProductionConfigurationValidator(IyzicoProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validate();
    }

    void validate() {
        if (!properties.enabled() || hasExplicitNonProductionProfile()) {
            return;
        }
        URI provider = parseHttps(properties.baseUrl(), "Iyzico API base URL");
        String providerHost = normalizedHost(provider);
        if (!APPROVED_PROVIDER_HOSTS.contains(providerHost) || provider.getPort() != -1
                || provider.getUserInfo() != null || provider.getQuery() != null || provider.getFragment() != null) {
            throw new IllegalStateException("Iyzico API base URL must use an approved provider origin");
        }
        if (!provider.getPath().isEmpty() && !"/".equals(provider.getPath())) {
            throw new IllegalStateException("Iyzico API base URL must use an approved provider origin");
        }

        URI callback = parseHttps(properties.callbackUrl(), "Iyzico callback URL");
        String callbackHost = normalizedHost(callback);
        if (callback.getUserInfo() != null || callbackHost.equals("localhost")
                || callbackHost.endsWith(".localhost") || callbackHost.endsWith(".local")
                || callbackHost.endsWith(".internal") || isPrivateOrLoopbackLiteral(callbackHost)) {
            throw new IllegalStateException("Iyzico callback URL must use a public HTTPS origin");
        }
    }

    private boolean hasExplicitNonProductionProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if (NON_PRODUCTION_PROFILES.contains(profile.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static URI parseHttps(String value, String label) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalStateException(label + " must be an absolute HTTPS URL");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(label + " is invalid", exception);
        }
    }

    private static String normalizedHost(URI uri) {
        return uri.getHost().toLowerCase(Locale.ROOT);
    }

    private static boolean isPrivateOrLoopbackLiteral(String host) {
        if (!isIpLiteral(host)) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            byte[] bytes = address.getAddress();
            boolean carrierGradeNat = bytes.length == 4
                    && Byte.toUnsignedInt(bytes[0]) == 100
                    && (Byte.toUnsignedInt(bytes[1]) & 0b1100_0000) == 64;
            return address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress() || carrierGradeNat;
        } catch (UnknownHostException exception) {
            return true;
        }
    }

    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.chars().allMatch(character -> Character.isDigit(character) || character == '.');
    }
}
