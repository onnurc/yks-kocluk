package com.ykskocluk.demo.security.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private RateLimitProperties properties;
    private ClientIpResolver ipResolver;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        ipResolver = new ClientIpResolver(properties);
    }

    @Test
    void resolveIp_trustProxyHeadersDisabled_usesRemoteAddress() {
        properties.setTrustProxyHeaders(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.5");
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18");

        String resolved = ipResolver.resolveIp(request);
        assertThat(resolved).isEqualTo("192.168.1.5");
    }

    @Test
    void resolveIp_trustProxyHeadersEnabled_usesFirstIpInForwardedForHeader() {
        properties.setTrustProxyHeaders(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.5");
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18");

        String resolved = ipResolver.resolveIp(request);
        assertThat(resolved).isEqualTo("203.0.113.195");
    }

    @Test
    void resolveIp_trustProxyHeadersEnabledButHeaderEmpty_fallsBackToRemoteAddress() {
        properties.setTrustProxyHeaders(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.5");
        request.addHeader("X-Forwarded-For", "");

        String resolved = ipResolver.resolveIp(request);
        assertThat(resolved).isEqualTo("192.168.1.5");
    }

    @Test
    void resolveIp_trustProxyHeadersEnabledButHeaderMissing_fallsBackToRemoteAddress() {
        properties.setTrustProxyHeaders(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.5");

        String resolved = ipResolver.resolveIp(request);
        assertThat(resolved).isEqualTo("192.168.1.5");
    }
}
