package com.url.shortener.service;

import com.url.shortener.dtos.UrlScanResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class UrlScanCacheServiceTest {

    private UrlScanCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new UrlScanCacheService();
        ReflectionTestUtils.setField(cacheService, "cacheEnabled", true);
        ReflectionTestUtils.setField(cacheService, "ttlSeconds", 60L);
        ReflectionTestUtils.setField(cacheService, "maxSize", 500);
    }

    @Test
    void shouldReturnCachedResponseWhenEntryExists() {
        UrlScanResponse response = new UrlScanResponse("SAFE", "Done", "https://example.com", 0, List.of("ok"));

        cacheService.put("https://example.com", response);
        UrlScanResponse cached = cacheService.get("https://example.com");

        assertNotNull(cached);
        assertEquals("SAFE", cached.getStatus());
        assertEquals("https://example.com", cached.getScannedUrl());
    }

    @Test
    void shouldReturnNullWhenCacheDisabled() {
        ReflectionTestUtils.setField(cacheService, "cacheEnabled", false);
        UrlScanResponse response = new UrlScanResponse("SAFE", "Done", "https://example.com", 0, List.of("ok"));

        cacheService.put("https://example.com", response);
        UrlScanResponse cached = cacheService.get("https://example.com");

        assertNull(cached);
    }
}

