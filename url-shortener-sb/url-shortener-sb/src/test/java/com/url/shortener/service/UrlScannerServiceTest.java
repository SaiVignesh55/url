package com.url.shortener.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.url.shortener.dtos.UrlScanResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlScannerServiceTest {

    private UrlScannerService urlScannerService;

    @BeforeEach
    void setUp() {
        urlScannerService = new UrlScannerService(new ObjectMapper());
        ReflectionTestUtils.setField(urlScannerService, "apiKey", "");
        ReflectionTestUtils.setField(urlScannerService, "safeBrowsingUrl", "https://safebrowsing.googleapis.com/v4/threatMatches:find");
    }

    @Test
    void shouldReturnSafeForSimpleHttpsUrl() {
        UrlScanResponse response = urlScannerService.scanUrl("https://example.com");

        assertEquals("SAFE", response.getStatus());
        assertEquals(0, response.getRiskScore());
        assertTrue(response.getReasons().contains("Google Safe Browsing API key is missing, external check skipped"));
    }

    @Test
    void shouldReturnSuspiciousForHttpUrlWithKeywords() {
        UrlScanResponse response = urlScannerService.scanUrl("http://promo-login-free-gift.example.com/wallet/update");

        assertEquals("SUSPICIOUS", response.getStatus());
        assertEquals(40, response.getRiskScore());
        assertTrue(response.getReasons().stream().anyMatch(r -> r.contains("URL is not using HTTPS")));
        assertTrue(response.getReasons().stream().anyMatch(r -> r.contains("Suspicious keywords found")));
    }

    @Test
    void shouldReturnUnsafeWhenRulesCrossUnsafeThreshold() {
        String longUrl = "http://example.com/login/" + "a".repeat(130);
        UrlScanResponse response = urlScannerService.scanUrl(longUrl);

        assertEquals("UNSAFE", response.getStatus());
        assertEquals(60, response.getRiskScore());
        assertTrue(response.getReasons().stream().anyMatch(r -> r.contains("URL length is unusually long")));
    }
}

