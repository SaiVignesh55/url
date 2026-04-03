package com.url.shortener.service;

import com.url.shortener.exception.InvalidUrlException;
import com.url.shortener.models.UrlMapping;
import com.url.shortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UrlResolverServiceTest {

    private UrlResolverService service;
    private RestTemplate restTemplate;
    private UrlMappingRepository urlMappingRepository;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        urlMappingRepository = mock(UrlMappingRepository.class);
        UrlNormalizationService normalizationService = new UrlNormalizationService();
        ReflectionTestUtils.setField(normalizationService, "maxUrlLength", 2048);

        HttpRetryService retryService = new HttpRetryService();
        ReflectionTestUtils.setField(retryService, "maxAttempts", 1);
        ReflectionTestUtils.setField(retryService, "initialDelayMs", 50L);

        service = new UrlResolverService(restTemplate, normalizationService, new SsrfProtectionService(), retryService, urlMappingRepository);
        ReflectionTestUtils.setField(service, "maxRedirects", 10);
        ReflectionTestUtils.setField(service, "internalHostsCsv", "localhost,127.0.0.1");
        ReflectionTestUtils.setField(service, "appBaseUrl", "https://short.local");
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

        assertTrue(result.loopDetected());
        assertEquals(List.of("https://example.com/", "https://example.com/"), result.chain());
    }

    @Test
    void shouldResolveInternalShortUrlFromDatabaseWithoutHttpCall() {
        UrlMapping mapping = new UrlMapping();
        mapping.setShortUrl("abc123");
        mapping.setOriginalUrl("https://google.com");

        when(urlMappingRepository.findByShortUrl("abc123")).thenReturn(mapping);

        UrlResolverService.ResolvedResult result = service.resolveUrl("http://localhost:9000/r/abc123");

        assertEquals("https://google.com/", result.finalUrl());
        assertEquals(List.of("http://localhost:9000/r/abc123", "https://google.com/"), result.chain());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void shouldRejectUnknownInternalShortCode() {
        when(urlMappingRepository.findByShortUrl("missing")).thenReturn(null);

        assertThrows(InvalidUrlException.class, () -> service.resolveUrl("http://localhost:9000/r/missing"));
    }

    @Test
    void shouldDetectInternalShortUrlFromConfiguredAppDomain() {
        assertTrue(service.isInternalShortUrl("https://short.local/r/abc123"));
        assertFalse(service.isInternalShortUrl("https://bit.ly/r/abc123"));
    }

    @Test
    void shouldResolveInternalUrlByMethodWithoutHttpCall() {
        UrlMapping mapping = new UrlMapping();
        mapping.setShortUrl("abc123");
        mapping.setOriginalUrl("https://docs.spring.io");
        when(urlMappingRepository.findByShortUrl("abc123")).thenReturn(mapping);

        String resolved = service.resolveInternalUrl("http://localhost:9000/r/abc123");

        assertEquals("https://docs.spring.io/", resolved);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void shouldResolveExternalShortUrlViaHttpRedirects() {
        HttpHeaders redirect = new HttpHeaders();
        redirect.add(HttpHeaders.LOCATION, "https://example.com/final");

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>("", redirect, HttpStatus.MOVED_PERMANENTLY))
                .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        UrlResolverService.ResolvedResult result = service.resolveUrl("https://bit.ly/xyz");

        assertEquals("https://example.com/final", result.finalUrl());
        assertEquals(List.of("https://bit.ly/xyz", "https://example.com/final"), result.chain());
    }

    @Test
    void shouldKeepDirectUrlWhenNoRedirectExists() {
        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        UrlResolverService.ResolvedResult result = service.resolveUrl("https://google.com");

        assertEquals("https://google.com/", result.finalUrl());
        assertEquals(List.of("https://google.com/"), result.chain());
    }
}

