package com.url.shortener.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.url.shortener.dtos.UrlScanAsyncStatusResponse;
import com.url.shortener.dtos.UrlScanHistoryResponse;
import com.url.shortener.dtos.UrlScanResponse;
import com.url.shortener.models.UrlMapping;
import com.url.shortener.models.UrlScanResult;
import com.url.shortener.repository.UrlMappingRepository;
import com.url.shortener.repository.UrlScanResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UrlScannerServiceTest {

    private UrlScannerService urlScannerService;
    private UrlResolverService urlResolverService;
    private UrlMappingRepository urlMappingRepository;
    private UrlScanResultRepository urlScanResultRepository;
    private UrlScanCacheService urlScanCacheService;

    @BeforeEach
    void setUp() {
        urlResolverService = mock(UrlResolverService.class);
        urlMappingRepository = mock(UrlMappingRepository.class);
        urlScanResultRepository = mock(UrlScanResultRepository.class);
        urlScanCacheService = mock(UrlScanCacheService.class);
        when(urlScanResultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(urlResolverService.resolveFinalUrl(any())).thenAnswer(invocation -> invocation.getArgument(0));
        urlScannerService = new UrlScannerService(new ObjectMapper(), urlResolverService, urlMappingRepository, urlScanResultRepository, urlScanCacheService);
        ReflectionTestUtils.setField(urlScannerService, "apiKey", "");
        ReflectionTestUtils.setField(urlScannerService, "safeBrowsingUrl", "https://safebrowsing.googleapis.com/v4/threatMatches:find");
        ReflectionTestUtils.setField(urlScannerService, "maxUrlLength", 120);
        ReflectionTestUtils.setField(urlScannerService, "noHttpsScore", 20);
        ReflectionTestUtils.setField(urlScannerService, "longUrlScore", 20);
        ReflectionTestUtils.setField(urlScannerService, "suspiciousKeywordsScore", 20);
        ReflectionTestUtils.setField(urlScannerService, "googleThreatScore", 60);
        ReflectionTestUtils.setField(urlScannerService, "suspiciousKeywordsCsv", "login,verify,bank,password,free,gift,wallet,update");
        ReflectionTestUtils.setField(urlScannerService, "suspiciousThreshold", 30);
        ReflectionTestUtils.setField(urlScannerService, "unsafeThreshold", 60);
        ReflectionTestUtils.setField(urlScannerService, "asyncJobRetentionSeconds", 900L);
        ReflectionTestUtils.setField(urlScannerService, "maxAsyncJobs", 200);
    }

    @Test
    void shouldReturnSafeForSimpleHttpsUrl() {
        UrlScanResponse response = urlScannerService.scanUrl("https://example.com");

        assertEquals("SAFE", response.getStatus());
        assertEquals(0, response.getRiskScore());
        assertTrue(response.getReasons().contains("Google Safe Browsing API key is missing, external check skipped"));
    }

    @Test
    void shouldResolveFrontendShortUrlBeforeScanning() {
        UrlMapping mapping = new UrlMapping();
        mapping.setOriginalUrl("https://very-long-domain.com/product?x=1&y=2");
        when(urlMappingRepository.findByShortUrl("abc123")).thenReturn(mapping);

        UrlScanResponse response = urlScannerService.scanUrl("http://localhost:5173/s/abc123");

        assertEquals("https://very-long-domain.com/product?x=1&y=2", response.getScannedUrl());
        assertEquals("SAFE", response.getStatus());
    }

    @Test
    void shouldScanResolvedRedirectUrl() {
        when(urlResolverService.resolveFinalUrl("https://bit.ly/demo")).thenReturn("https://example.com/final");

        UrlScanResponse response = urlScannerService.scanUrl("https://bit.ly/demo");

        assertEquals("https://example.com/final", response.getScannedUrl());
        verify(urlResolverService, times(1)).resolveFinalUrl("https://bit.ly/demo");
    }

    @Test
    void shouldReturnInvalidRequestWhenShortCodeNotFound() {
        when(urlMappingRepository.findByShortUrl("missing")).thenReturn(null);

        UrlScanResponse response = urlScannerService.scanUrl("http://localhost:5173/s/missing");

        assertEquals("INVALID_REQUEST", response.getStatus());
        assertEquals("Invalid short URL", response.getMessage());
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

    @Test
    void shouldParseSuspiciousKeywordsFromConfigWithSpacesAndMixedCase() {
        ReflectionTestUtils.setField(urlScannerService, "suspiciousKeywordsCsv", " Login , FREE , Verify ");

        UrlScanResponse response = urlScannerService.scanUrl("http://example.com/login/free");

        assertEquals("SUSPICIOUS", response.getStatus());
        assertEquals(40, response.getRiskScore());
        assertTrue(response.getReasons().stream().anyMatch(r -> r.contains("Suspicious keywords found")));
    }

    @Test
    void shouldReturnCachedResultWithoutSavingNewScan() {
        UrlScanResponse cached = new UrlScanResponse(
                "SAFE",
                "Cached scan response",
                "https://example.com",
                0,
                List.of("From cache")
        );

        when(urlScanCacheService.get("https://example.com")).thenReturn(cached);

        UrlScanResponse response = urlScannerService.scanUrl("https://example.com");

        assertEquals("SAFE", response.getStatus());
        assertEquals("Cached scan response", response.getMessage());
        verify(urlScanResultRepository, never()).save(any());
        verify(urlScanCacheService, times(1)).get("https://example.com");
        verify(urlScanCacheService, never()).put(any(), any());
    }

    @Test
    void shouldReturnLimitedHistoryResults() {
        UrlScanResult first = new UrlScanResult();
        first.setScannedUrl("https://one.com");
        UrlScanResult second = new UrlScanResult();
        second.setScannedUrl("https://two.com");
        UrlScanResult third = new UrlScanResult();
        third.setScannedUrl("https://three.com");

        when(urlScanResultRepository.findAll(any(Pageable.class))).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(0);
            List<UrlScanResult> all = List.of(first, second, third);
            int end = Math.min(pageable.getPageSize(), all.size());
            return new PageImpl<>(all.subList(0, end));
        });

        List<UrlScanHistoryResponse> history = urlScannerService.getScanHistory(2, null);

        assertEquals(2, history.size());
        assertEquals("https://one.com", history.get(0).getScannedUrl());
        assertEquals("https://two.com", history.get(1).getScannedUrl());
    }

    @Test
    void shouldReturnFilteredHistoryByStatus() {
        UrlScanResult unsafeItem = new UrlScanResult();
        unsafeItem.setScannedUrl("http://bad.com");
        unsafeItem.setStatus("UNSAFE");

        when(urlScanResultRepository.findByStatusIgnoreCase(eq("UNSAFE"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(unsafeItem)));

        List<UrlScanHistoryResponse> history = urlScannerService.getScanHistory(20, "UNSAFE");

        assertEquals(1, history.size());
        assertEquals("UNSAFE", history.get(0).getStatus());
        assertEquals("http://bad.com", history.get(0).getScannedUrl());
    }

    @Test
    void shouldReturnHistoryByIdWhenExists() {
        UrlScanResult saved = new UrlScanResult();
        saved.setId(10L);
        saved.setScannedUrl("https://example.com");
        saved.setStatus("SAFE");
        saved.setMessage("Scan completed");
        saved.setRiskScore(0);
        saved.setReasons("Google Safe Browsing found no threat match");

        when(urlScanResultRepository.findById(10L)).thenReturn(Optional.of(saved));

        UrlScanHistoryResponse response = urlScannerService.getScanHistoryById(10L);

        assertEquals(10L, response.getId());
        assertEquals("SAFE", response.getStatus());
    }

    @Test
    void shouldThrowWhenHistoryIdNotFound() {
        when(urlScanResultRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> urlScannerService.getScanHistoryById(999L));
    }

    @Test
    void shouldClearCacheThroughService() {
        urlScannerService.clearScanCache();

        verify(urlScanCacheService, times(1)).clear();
    }

    @Test
    void shouldReturnAsyncScanResult() {
        CompletableFuture<UrlScanResponse> future = urlScannerService.scanUrlAsync("https://example.com");
        UrlScanResponse response = future.join();

        assertEquals("SAFE", response.getStatus());
        assertEquals("https://example.com", response.getScannedUrl());
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

        assertEquals("COMPLETED", statusResponse.getStatus());
        assertEquals("https://example.com", statusResponse.getResult().getScannedUrl());
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

    @Test
    void shouldExpireCompletedAsyncJobWhenRetentionIsZero() throws InterruptedException {
        String jobId = urlScannerService.submitAsyncScan("https://example.com");

        for (int i = 0; i < 20; i++) {
            UrlScanAsyncStatusResponse status = urlScannerService.getAsyncScanStatus(jobId);
            if ("COMPLETED".equals(status.getStatus())) {
                break;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }

        ReflectionTestUtils.setField(urlScannerService, "asyncJobRetentionSeconds", 0L);
        TimeUnit.MILLISECONDS.sleep(5);

        assertThrows(ResponseStatusException.class, () -> urlScannerService.getAsyncScanStatus(jobId));
    }

    @Test
    void shouldCleanupExpiredJobsWhenScheduledCleanupRuns() throws InterruptedException {
        String jobId = urlScannerService.submitAsyncScan("https://example.com");

        UrlScanAsyncStatusResponse status = null;
        for (int i = 0; i < 20; i++) {
            status = urlScannerService.getAsyncScanStatus(jobId);
            if ("COMPLETED".equals(status.getStatus())) {
                break;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }

        assertNotNull(status);
        ReflectionTestUtils.setField(urlScannerService, "asyncJobRetentionSeconds", 0L);
        urlScannerService.runScheduledAsyncCleanup();

        assertThrows(ResponseStatusException.class, () -> urlScannerService.getAsyncScanStatus(jobId));
    }
}

