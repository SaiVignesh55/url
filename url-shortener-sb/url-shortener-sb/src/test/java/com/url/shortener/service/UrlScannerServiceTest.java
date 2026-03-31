package com.url.shortener.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.url.shortener.dtos.UrlScanAsyncStatusResponse;
import com.url.shortener.dtos.UrlScanResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UrlScannerServiceTest {

    private UrlScannerService urlScannerService;
    private UrlResolverService urlResolverService;
    private UrlScanCacheService urlScanCacheService;

    @BeforeEach
    void setUp() {
        urlResolverService = mock(UrlResolverService.class);
        urlScanCacheService = mock(UrlScanCacheService.class);

        when(urlResolverService.resolveUrl(any())).thenAnswer(invocation -> {
            String input = invocation.getArgument(0);
            return new UrlResolverService.ResolvedResult(input, List.of(input));
        });
        when(urlScanCacheService.get(any())).thenReturn(null);

        urlScannerService = new UrlScannerService(new ObjectMapper(), urlResolverService, urlScanCacheService);
        ReflectionTestUtils.setField(urlScannerService, "safeBrowsingApiKey", "");
        ReflectionTestUtils.setField(urlScannerService, "safeBrowsingUrl", "https://safebrowsing.googleapis.com/v4/threatMatches:find");
        ReflectionTestUtils.setField(urlScannerService, "virusTotalApiKey", "");
        ReflectionTestUtils.setField(urlScannerService, "urlscanApiKey", "");
        ReflectionTestUtils.setField(urlScannerService, "suspiciousTldsCsv", "xyz,tk,top");
        ReflectionTestUtils.setField(urlScannerService, "adKeywordsCsv", "ads,click,redirect,banner,track,tracker");
        ReflectionTestUtils.setField(urlScannerService, "piracyKeywordsCsv", "movie,download,watch free,torrent");
        ReflectionTestUtils.setField(urlScannerService, "maxUrlLength", 120);
        ReflectionTestUtils.setField(urlScannerService, "asyncTimeoutMs", 20000L);
        ReflectionTestUtils.setField(urlScannerService, "asyncJobRetentionSeconds", 900L);
        ReflectionTestUtils.setField(urlScannerService, "trustedDomainsCsv", "facebook.com,google.com,twitter.com");
        ReflectionTestUtils.setField(urlScannerService, "maxAsyncJobs", 200);
    }

    @Test
    void shouldReturnInvalidRequestWhenUrlMissing() {
        UrlScanResponse response = urlScannerService.scanUrl("   ");

        assertEquals("INVALID_REQUEST", response.getStatus());
        assertTrue(response.getReasons().contains("Please provide a valid URL in request body"));
    }

    @Test
    void shouldUseResolvedFinalUrlAndRedirectChain() {
        when(urlResolverService.resolveUrl("https://bit.ly/demo"))
                .thenReturn(new UrlResolverService.ResolvedResult(
                        "https://example.com/final",
                        List.of("https://bit.ly/demo", "https://example.com/final")
                ));

        UrlScanResponse response = urlScannerService.scanUrl("https://bit.ly/demo");

        assertEquals("https://bit.ly/demo", response.getScannedUrl());
        assertEquals("https://example.com/final", response.getFinalUrl());
        assertEquals(2, response.getRedirectChain().size());
        assertTrue(response.getChecksPerformed().stream().anyMatch(c -> c.contains("Redirect resolution")));
    }

    @Test
    void shouldMarkPiracyDomainAsSuspicious() {
        when(urlResolverService.resolveUrl("https://short.site/x"))
                .thenReturn(new UrlResolverService.ResolvedResult(
                        "https://movierulz.example/watch",
                        List.of("https://short.site/x", "https://movierulz.example/watch")
                ));

        UrlScanResponse response = urlScannerService.scanUrl("https://short.site/x");

        assertEquals("SUSPICIOUS", response.getStatus());
        assertTrue(response.getBreakdown().get("piracy") >= 70);
        assertTrue(response.getReasons().contains("Piracy domain detected"));
    }

    @Test
    void shouldReturnCachedResultForSameFinalUrl() {
        UrlScanResponse cached = new UrlScanResponse(
                "SAFE",
                "Cached response",
                "https://input.com",
                1,
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                new ArrayList<>(),
                List.of("From cache"),
                List.of("https://input.com", "https://example.com/final"),
                "https://example.com/final",
                new ArrayList<>(),
                0,
                "",
                ""
        );

        when(urlResolverService.resolveUrl("https://input.com"))
                .thenReturn(new UrlResolverService.ResolvedResult(
                        "https://example.com/final",
                        List.of("https://input.com", "https://example.com/final")
                ));
        when(urlScanCacheService.get("https://example.com/final")).thenReturn(cached);

        UrlScanResponse response = urlScannerService.scanUrl("https://input.com");

        assertEquals("Cached response", response.getMessage());
        assertEquals("https://input.com", response.getScannedUrl());
        assertEquals("https://example.com/final", response.getFinalUrl());
        verify(urlScanCacheService, times(1)).get("https://example.com/final");
    }

    @Test
    void shouldSubmitAndCompleteAsyncJob() throws InterruptedException {
        String jobId = urlScannerService.submitAsyncScan("https://example.com");

        UrlScanAsyncStatusResponse statusResponse = null;
        for (int i = 0; i < 20; i++) {
            statusResponse = urlScannerService.getAsyncScanStatus(jobId);
            if (!"PENDING".equals(statusResponse.getStatus())) {
                break;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }

        assertNotNull(statusResponse);
        assertEquals("COMPLETED", statusResponse.getStatus());
    }

    @Test
    void shouldThrowWhenAsyncJobNotFound() {
        assertThrows(ResponseStatusException.class, () -> urlScannerService.getAsyncScanStatus("missing-job-id"));
    }

    @Test
    void shouldRejectAsyncSubmissionWhenMaxPendingJobsReached() {
        ReflectionTestUtils.setField(urlScannerService, "maxAsyncJobs", 0);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> urlScannerService.submitAsyncScan("https://example.com"));

        assertEquals(429, ex.getStatusCode().value());
    }
}

