package com.url.shortener.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScanRateLimitFilterTest {

    private ScanRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ScanRateLimitFilter(new IpAddressResolver("127.0.0.0/8,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,::1/128,fc00::/7,fe80::/10"));
        ReflectionTestUtils.setField(filter, "ipPerMinute", 1);
        ReflectionTestUtils.setField(filter, "userPerMinute", 1);
        ReflectionTestUtils.setField(filter, "userPerDay", 1);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectUnauthenticatedRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/scan");
        request.setRemoteAddr("8.8.8.8");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldApplyRateLimit() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("tester", "N/A")
        );

        MockHttpServletRequest request1 = new MockHttpServletRequest("POST", "/api/scan");
        request1.setRemoteAddr("8.8.8.8");
        MockHttpServletResponse response1 = new MockHttpServletResponse();

        filter.doFilter(request1, response1, (req, res) -> {});
        assertEquals(200, response1.getStatus());

        MockHttpServletRequest request2 = new MockHttpServletRequest("POST", "/api/scan");
        request2.setRemoteAddr("8.8.8.8");
        MockHttpServletResponse response2 = new MockHttpServletResponse();

        filter.doFilter(request2, response2, (req, res) -> {});
        assertEquals(429, response2.getStatus());
    }
}

