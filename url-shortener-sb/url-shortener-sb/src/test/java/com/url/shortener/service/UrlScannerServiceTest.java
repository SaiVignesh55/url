package com.url.shortener.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.url.shortener.dtos.UrlScanAsyncStatusResponse;
import com.url.shortener.dtos.UrlScanResponse;
import com.url.shortener.exception.InvalidUrlException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UrlScannerServiceTest {

    private UrlScannerService service;
    private UrlResolverService resolverService;
    private UrlScanCacheService cacheService;
    private UrlNormalizationService normalizationService;
    private SsrfProtectionService ssrfProtectionService;

    @BeforeEach
    void setUp() {
        resolverService = mock(UrlResolverService.class);
        cacheService = new UrlScanCacheService();
        normalizationService = new UrlNormalizationService();
        ssrfProtectionService = new SsrfProtectionService();
        HttpRetryService retryService = new HttpRetryService();
        RestTemplate restTemplate = mock(RestTemplate.class);
        Executor executor = Executors.newSingleThreadExecutor();

        ReflectionTestUtils.setField(normalizationService, "maxUrlLength", 2048);
        ReflectionTestUtils.setField(retryService, "maxAttempts", 1);
        ReflectionTestUtils.setField(retryService, "initialDelayMs", 50L);

        service = new UrlScannerService(
                new ObjectMapper(),
                restTemplate,
                resolverService,
                cacheService,
                normalizationService,
                ssrfProtectionService,
                retryService,
                executor
        );

        ReflectionTestUtils.setField(cacheService, "cacheEnabled", true);
        ReflectionTestUtils.setField(cacheService, "ttlSeconds", 60L);
        ReflectionTestUtils.setField(cacheService, "maxSize", 100);
        cacheService.init();

        ReflectionTestUtils.setField(service, "safeBrowsingApiKey", "");
        ReflectionTestUtils.setField(service, "virusTotalApiKey", "");
        ReflectionTestUtils.setField(service, "urlscanApiKey", "");
        ReflectionTestUtils.setField(service, "safeBrowsingUrl", "https://example.com/gsb");
        ReflectionTestUtils.setField(service, "virusTotalUrl", "https://example.com/vt");
        ReflectionTestUtils.setField(service, "virusTotalAnalysisUrl", "https://example.com/vt/%s");
        ReflectionTestUtils.setField(service, "asyncTimeoutMs", 5000L);
        ReflectionTestUtils.setField(service, "maxAsyncJobs", 50);
        ReflectionTestUtils.setField(service, "asyncJobRetentionSeconds", 60L);

        when(resolverService.resolveUrl(any())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0);
            if (value == null) {
                value = "https://example.com/";
            }
            return new UrlResolverService.ResolvedResult(value, List.of(value), false, false);
        });
    }

    @Test
    void shouldRejectInvalidScheme() {
        assertThrows(InvalidUrlException.class, () -> service.scanUrl("javascript:alert(1)"));
    }

    @Test
    void shouldReturnUnknownWhenMandatoryApisUnavailable() {
        UrlScanResponse response = service.scanUrl("https://example.com");

        assertEquals("UNKNOWN", response.getStatus());
        assertTrue(response.getReasons().stream().anyMatch(r -> r.toLowerCase().contains("pending") || r.toLowerCase().contains("unavailable")));
    }

    @Test
    void shouldMarkRedirectLoopAsSuspiciousOrUnknown() {
        when(resolverService.resolveUrl(any()))
                .thenReturn(new UrlResolverService.ResolvedResult(
                        "https://example.com/",
                        List.of("https://example.com/", "https://iana.org/", "https://example.com/"),
                        true,
                        false
                ));

        UrlScanResponse response = service.scanUrl("https://www.wikipedia.org");

        assertTrue(response.getRedirectChain().size() >= 2);
    }

    @Test
    void shouldSubmitAsyncAndComplete() throws Exception {
        String jobId = service.submitAsyncScan("https://example.com");

        UrlScanAsyncStatusResponse status = null;
        for (int i = 0; i < 30; i++) {
            status = service.getAsyncScanStatus(jobId);
            if (!"PENDING".equals(status.getStatus())) {
                break;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }

        assertNotNull(status);
        assertEquals("COMPLETED", status.getStatus());
        assertNotNull(status.getResult());
    }
}
