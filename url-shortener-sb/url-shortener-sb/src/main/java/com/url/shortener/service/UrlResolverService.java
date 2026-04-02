package com.url.shortener.service;

import com.url.shortener.exception.InvalidUrlException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UrlResolverService {

    @Value("${url.scan.redirect.max-depth:10}")
    private int maxRedirects;

    private final RestTemplate scannerRestTemplate;
    private final UrlNormalizationService urlNormalizationService;
    private final SsrfProtectionService ssrfProtectionService;
    private final HttpRetryService retryService;

    public ResolvedResult resolveUrl(String inputUrl) {
        UrlNormalizationService.NormalizedUrl normalized = urlNormalizationService.normalizeAndValidate(inputUrl);
        URI current = normalized.uri();

        List<String> chain = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        chain.add(current.toString());
        visited.add(current.toString());

        for (int hop = 0; hop < Math.max(1, maxRedirects); hop++) {
            ssrfProtectionService.assertPublicDestination(current);
            String location = readLocationHeader(current);
            if (location == null || location.isBlank()) {
                return new ResolvedResult(current.toString(), chain, false, false);
            }

            URI next = current.resolve(location);
            UrlNormalizationService.NormalizedUrl nextNormalized = urlNormalizationService.normalizeAndValidate(next.toString());
            current = nextNormalized.uri();

            ssrfProtectionService.assertPublicDestination(current);

            String canonical = current.toString();
            chain.add(canonical);
            if (!visited.add(canonical)) {
                return new ResolvedResult(canonical, chain, true, false);
            }
        }

        return new ResolvedResult(current.toString(), chain, false, true);
    }

    private String readLocationHeader(URI current) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "UrlScanner/2.0");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = executeRequest(current, HttpMethod.HEAD, request);
        if (isRedirect(response.getStatusCode())) {
            return response.getHeaders().getFirst(HttpHeaders.LOCATION);
        }

        if (response.getStatusCode().value() == 405 || response.getStatusCode().value() == 501) {
            ResponseEntity<String> fallback = executeRequest(current, HttpMethod.GET, request);
            if (isRedirect(fallback.getStatusCode())) {
                return fallback.getHeaders().getFirst(HttpHeaders.LOCATION);
            }
        }

        return null;
    }

    private ResponseEntity<String> executeRequest(URI uri, HttpMethod method, HttpEntity<Void> request) {
        try {
            return retryService.execute(() -> scannerRestTemplate.exchange(uri, method, request, String.class));
        } catch (Exception ex) {
            return ResponseEntity.ok().build();
        }
    }

    private boolean isRedirect(HttpStatusCode statusCode) {
        int status = statusCode.value();
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    public record ResolvedResult(String finalUrl,
                                 List<String> chain,
                                 boolean loopDetected,
                                 boolean maxDepthReached) {
    }
}
