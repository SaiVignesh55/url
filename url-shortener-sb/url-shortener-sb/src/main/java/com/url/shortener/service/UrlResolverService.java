package com.url.shortener.service;

import com.url.shortener.exception.InvalidUrlException;
import com.url.shortener.models.UrlMapping;
import com.url.shortener.repository.UrlMappingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UrlResolverService {

    private static final Logger log = LoggerFactory.getLogger(UrlResolverService.class);

    @Value("${url.scan.redirect.max-depth:10}")
    private int maxRedirects;

    @Value("${url.scan.internal-hosts:localhost,127.0.0.1}")
    private String internalHostsCsv;

    @Value("${app.base-url:http://localhost:9001}")
    private String appBaseUrl;

    private final RestTemplate scannerRestTemplate;
    private final UrlNormalizationService urlNormalizationService;
    private final SsrfProtectionService ssrfProtectionService;
    private final HttpRetryService retryService;
    private final UrlMappingRepository urlMappingRepository;

    public ResolvedResult resolveUrl(String inputUrl) {
        UrlNormalizationService.NormalizedUrl normalized = urlNormalizationService.normalizeAndValidate(inputUrl);
        String normalizedInput = normalized.normalizedUrl();
        URI current = normalized.uri();

        log.info("Resolving URL input={}", normalizedInput);

        if (isInternalShortUrl(normalizedInput)) {
            log.info("Detected internal short URL. Bypassing HTTP resolver for input={}", normalizedInput);
            String resolvedInternalUrl = resolveInternalUrl(normalizedInput);
            log.info("Resolved URL via DB input={} final={}", normalizedInput, resolvedInternalUrl);
            return new ResolvedResult(resolvedInternalUrl, List.of(normalizedInput, resolvedInternalUrl), false, false);
        }

        log.info("Resolved URL path type=external-http input={}", normalizedInput);
        List<String> chain = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        chain.add(current.toString());
        visited.add(current.toString());

        for (int hop = 0; hop < Math.max(1, maxRedirects); hop++) {
            ssrfProtectionService.assertPublicDestination(current);
            String location = readLocationHeader(current);
            if (location == null || location.isBlank()) {
                log.info("Resolved URL via HTTP input={} final={}", normalizedInput, current);
                return new ResolvedResult(current.toString(), chain, false, false);
            }

            URI next = current.resolve(location);
            UrlNormalizationService.NormalizedUrl nextNormalized = urlNormalizationService.normalizeAndValidate(next.toString());
            current = nextNormalized.uri();

            ssrfProtectionService.assertPublicDestination(current);

            String canonical = current.toString();
            chain.add(canonical);
            if (!visited.add(canonical)) {
                log.info("Resolved URL via HTTP (loop) input={} final={}", normalizedInput, canonical);
                return new ResolvedResult(canonical, chain, true, false);
            }
        }

        log.info("Resolved URL via HTTP (max-depth) input={} final={}", normalizedInput, current);
        return new ResolvedResult(current.toString(), chain, false, true);
    }

    public String resolveInternalUrl(String url) {
        UrlNormalizationService.NormalizedUrl normalized = urlNormalizationService.normalizeAndValidate(url);
        URI uri = normalized.uri();
        if (!isInternalShortUrl(normalized.normalizedUrl())) {
            throw new InvalidUrlException("URL is not an internal short URL");
        }

        String code = extractShortCode(uri.getPath());
        if (code == null || code.isBlank()) {
            throw new InvalidUrlException("Invalid internal short URL");
        }

        UrlMapping mapping;
        try {
            mapping = urlMappingRepository.findByShortUrl(code);
        } catch (Exception ex) {
            throw new InvalidUrlException("Failed to resolve short URL from database");
        }

        if (mapping == null || mapping.getOriginalUrl() == null || mapping.getOriginalUrl().isBlank()) {
            throw new InvalidUrlException("Short URL not found");
        }

        UrlNormalizationService.NormalizedUrl target = urlNormalizationService.normalizeAndValidate(mapping.getOriginalUrl());
        String resolved = target.normalizedUrl();
        log.info("Resolved URL via DB code={} final={}", code, resolved);
        return resolved;
    }

    public boolean isInternalShortUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        URI uri;
        try {
            UrlNormalizationService.NormalizedUrl normalized = urlNormalizationService.normalizeAndValidate(url);
            uri = normalized.uri();
        } catch (Exception ex) {
            return false;
        }

        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();
        return path.contains("/r/") && isInternalHost(host);
    }

    private boolean isInternalHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }

        for (String token : getInternalHosts()) {
            String allowed = token.trim().toLowerCase();
            if (!allowed.isBlank() && allowed.equals(host)) {
                return true;
            }
        }
        return false;
    }

    private List<String> getInternalHosts() {
        List<String> hosts = new ArrayList<>();
        if (internalHostsCsv != null && !internalHostsCsv.isBlank()) {
            hosts.addAll(Arrays.stream(internalHostsCsv.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .toList());
        }

        String appHost = extractHost(appBaseUrl);
        if (!appHost.isBlank()) {
            hosts.add(appHost.toLowerCase(Locale.ROOT));
        }
        return hosts;
    }

    private String extractHost(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }

        String candidate = baseUrl.trim();
        if (!candidate.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")) {
            candidate = "https://" + candidate;
        }

        try {
            URI uri = URI.create(candidate);
            return uri.getHost() == null ? "" : uri.getHost();
        } catch (Exception ex) {
            return "";
        }
    }

    private String extractShortCode(String path) {
        if (path == null || !path.startsWith("/r/")) {
            return null;
        }
        String code = path.substring(3).trim();
        if (code.isEmpty() || code.contains("/")) {
            return null;
        }
        return code;
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
