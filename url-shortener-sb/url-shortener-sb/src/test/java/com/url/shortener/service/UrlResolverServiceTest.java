package com.url.shortener.service;

import com.url.shortener.exception.InvalidUrlException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UrlResolverServiceTest {

    private UrlResolverService service;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        UrlNormalizationService normalizationService = new UrlNormalizationService();
        ReflectionTestUtils.setField(normalizationService, "maxUrlLength", 2048);

        HttpRetryService retryService = new HttpRetryService();
        ReflectionTestUtils.setField(retryService, "maxAttempts", 1);
        ReflectionTestUtils.setField(retryService, "initialDelayMs", 50L);

        service = new UrlResolverService(restTemplate, normalizationService, new SsrfProtectionService(), retryService);
        ReflectionTestUtils.setField(service, "maxRedirects", 10);
    }

    @Test
    void shouldBlockPrivateHost() {
        assertThrows(InvalidUrlException.class, () -> service.resolveUrl("http://127.0.0.1/admin"));
    }

    @Test
    void shouldDetectRedirectLoop() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.LOCATION, "https://example.com/");
        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>("", headers, HttpStatus.MOVED_PERMANENTLY));

        UrlResolverService.ResolvedResult result = service.resolveUrl("https://example.com");

        assertEquals(true, result.loopDetected());
        assertEquals(List.of("https://example.com/", "https://example.com/"), result.chain());
    }
}

