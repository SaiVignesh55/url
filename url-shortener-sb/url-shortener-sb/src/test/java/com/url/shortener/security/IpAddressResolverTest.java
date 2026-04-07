package com.url.shortener.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IpAddressResolverTest {

    private static final String TRUSTED_CIDRS = "127.0.0.0/8,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,::1/128,fc00::/7,fe80::/10";

    @Test
    void shouldPickFirstIpFromXForwardedFor() {
        IpAddressResolver resolver = new IpAddressResolver(TRUSTED_CIDRS);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "192.168.1.10, 203.0.113.55, 127.0.0.1");

        String resolved = resolver.getClientIp(request);

        assertEquals("192.168.1.10", resolved);
    }

    @Test
    void shouldUseRemoteAddrWhenForwardedHeaderIsMissing() {
        IpAddressResolver resolver = new IpAddressResolver(TRUSTED_CIDRS);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.10");

        String resolved = resolver.getClientIp(request);

        assertEquals("198.51.100.10", resolved);
    }

    @Test
    void shouldPrioritizeXForwardedForOverXRealIp() {
        IpAddressResolver resolver = new IpAddressResolver(TRUSTED_CIDRS);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.10.10.10");
        request.addHeader("X-Forwarded-For", "127.0.0.1, 192.168.1.9");
        request.addHeader("X-Real-IP", "198.51.100.40");

        String resolved = resolver.getClientIp(request);

        assertEquals("127.0.0.1", resolved);
    }
}
