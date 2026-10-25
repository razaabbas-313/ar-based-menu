package com.atelier.media.security;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void untrustedRemoteAddrIgnoresSpoofedXForwardedFor() {
        AtelierProperties base = TestFixtures.atelierProperties();
        AtelierProperties props = new AtelierProperties(
                base.keyPrefix(), base.privateKeyPrefix(), base.presignTtl(), base.staleUploadTtl(),
                base.maxImageBytes(), base.maxModelBytes(), base.maxPublishedModelBytes(), base.maxBrandingBytes(),
                base.menuLimit(), base.directoryLimit(), base.jwt(), base.cors(), base.login(),
                base.superAdmin(), base.passwordReset(),
                List.of("10.0.0.1") // Only 10.0.0.1 is trusted proxy
        );
        ClientIpResolver resolver = new ClientIpResolver(props);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.50"); // Untrusted external IP
        request.addHeader("X-Forwarded-For", "198.51.100.1, 10.0.0.1"); // Spoofed header

        String resolved = resolver.resolveClientIp(request);
        assertThat(resolved).isEqualTo("203.0.113.50");
    }

    @Test
    void trustedProxyUsesFirstXForwardedForEntry() {
        AtelierProperties base = TestFixtures.atelierProperties();
        AtelierProperties props = new AtelierProperties(
                base.keyPrefix(), base.privateKeyPrefix(), base.presignTtl(), base.staleUploadTtl(),
                base.maxImageBytes(), base.maxModelBytes(), base.maxPublishedModelBytes(), base.maxBrandingBytes(),
                base.menuLimit(), base.directoryLimit(), base.jwt(), base.cors(), base.login(),
                base.superAdmin(), base.passwordReset(),
                List.of("10.0.0.1") // Trusted proxy
        );
        ClientIpResolver resolver = new ClientIpResolver(props);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1"); // Remote address is the trusted load balancer
        request.addHeader("X-Forwarded-For", "198.51.100.22, 10.0.0.2"); // Real client IP + intermediate proxy

        String resolved = resolver.resolveClientIp(request);
        assertThat(resolved).isEqualTo("198.51.100.22");
    }

    @Test
    void fallbackToRemoteAddrWhenNoForwardedHeader() {
        ClientIpResolver resolver = new ClientIpResolver(TestFixtures.atelierProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.15");

        String resolved = resolver.resolveClientIp(request);
        assertThat(resolved).isEqualTo("192.168.1.15");
    }
}
