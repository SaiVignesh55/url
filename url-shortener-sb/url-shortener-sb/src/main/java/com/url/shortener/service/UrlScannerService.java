package com.url.shortener.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.url.shortener.dtos.UrlScanAsyncStatusResponse;
import com.url.shortener.dtos.UrlScanResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

@Service
@RequiredArgsConstructor
public class UrlScannerService {

    private static final Logger log = LoggerFactory.getLogger(UrlScannerService.class);

    @Value("${google.safe-browsing.api-key:}")
    private String safeBrowsingApiKey;

    @Value("${google.safe-browsing.url}")
    private String safeBrowsingUrl;

    @Value("${virustotal.api-key:}")
    private String virusTotalApiKey;

    @Value("${virustotal.url:https://www.virustotal.com/api/v3/urls}")
    private String virusTotalUrl;

    @Value("${virustotal.analysis-url:https://www.virustotal.com/api/v3/analyses/%s}")
    private String virusTotalAnalysisUrl;

    @Value("${virustotal.poll.max-attempts:5}")
    private int virusTotalPollMaxAttempts;

    @Value("${virustotal.poll.delay-ms:1200}")
    private long virusTotalPollDelayMs;

    @Value("${urlscan.api-key:}")
    private String urlscanApiKey;

    @Value("${urlscan.submit-url:https://urlscan.io/api/v1/scan/}")
    private String urlscanSubmitUrl;

    @Value("${urlscan.result-url-template:https://urlscan.io/api/v1/result/%s/}")
    private String urlscanResultUrlTemplate;

    @Value("${urlscan.poll.max-attempts:6}")
    private int urlscanPollMaxAttempts;

    @Value("${urlscan.poll.delay-ms:1500}")
    private long urlscanPollDelayMs;

    @Value("${url.scan.async.timeout-ms:20000}")
    private long asyncTimeoutMs;

    @Value("${url.scan.async.job-retention-seconds:900}")
    private long asyncJobRetentionSeconds;

    @Value("${url.scan.async.max-jobs:200}")
    private int maxAsyncJobs;

    private final ObjectMapper objectMapper;
    private final RestTemplate scannerRestTemplate;
    private final UrlResolverService urlResolverService;
    private final UrlScanCacheService urlScanCacheService;
    private final UrlNormalizationService urlNormalizationService;
    private final SsrfProtectionService ssrfProtectionService;
    private final HttpRetryService retryService;

    @Qualifier("scanTaskExecutor")
    private final Executor scanTaskExecutor;

    private final Map<String, AsyncScanJob> asyncJobs = new ConcurrentHashMap<>();

    public UrlScanResponse scanUrl(String url) {
        UrlNormalizationService.NormalizedUrl normalized = urlNormalizationService.normalizeAndValidate(url);
        UrlScanResponse cached = urlScanCacheService.get(normalized.normalizedUrl());
        if (cached != null) {
            return cached;
        }

        UrlResolverService.ResolvedResult resolved = urlResolverService.resolveUrl(normalized.normalizedUrl());
        List<String> chain = resolved.chain() == null || resolved.chain().isEmpty()
                ? List.of(normalized.normalizedUrl())
                : resolved.chain();

        AggregatedSignals signals = evaluateAllHops(chain, normalized.potentialHomograph());

        UrlscanBehavior behavior = UrlscanBehavior.empty(chain.get(chain.size() - 1));
        if (urlscanApiKey != null && !urlscanApiKey.isBlank()) {
            behavior = scanWithUrlScan(urlNormalizationService.normalizeAndValidate(chain.get(chain.size() - 1)).urlscanSafeUrl());
            if (behavior.externalError) {
                signals.reasons.add("urlscan unavailable");
                signals.unknownSignals = true;
            } else {
                signals.reasons.add("urlscan behavior collected");
                if (behavior.scriptCount > 30 || behavior.contactedDomains.size() > 20) {
                    signals.suspiciousScore += 15;
                }
            }
        }

        if (resolved.loopDetected()) {
            signals.reasons.add("Redirect loop detected");
            signals.suspiciousScore += 30;
        }
        if (resolved.maxDepthReached()) {
            signals.reasons.add("Redirect depth limit reached");
            signals.suspiciousScore += 25;
            signals.unknownSignals = true;
        }

        int finalScore = calculateScore(signals);
        String status = determineStatus(signals, finalScore);

        UrlScanResponse response = new UrlScanResponse(
                status,
                buildMessage(status),
                normalized.normalizedUrl(),
                finalScore,
                buildBreakdown(signals),
                buildCategoryLabels(signals, finalScore),
                signals.checksPerformed,
                dedupe(signals.reasons),
                chain,
                chain.get(chain.size() - 1),
                behavior.contactedDomains,
                behavior.scriptCount,
                behavior.pageTitle,
                behavior.screenshotUrl
        );
        response.setTotalRequests(behavior.totalRequests);

        urlScanCacheService.put(normalized.normalizedUrl(), response);
        return response;
    }

    public String submitAsyncScan(String url) {
        cleanupExpiredAsyncJobs();
        long pendingJobs = asyncJobs.values().stream().filter(AsyncScanJob::isPending).count();
        if (pendingJobs >= Math.max(1, maxAsyncJobs)) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "Too many async scan jobs");
        }

        String jobId = UUID.randomUUID().toString();
        AsyncScanJob job = new AsyncScanJob(jobId);
        asyncJobs.put(jobId, job);

        CompletableFuture
                .supplyAsync(() -> scanUrl(url), scanTaskExecutor)
                .orTimeout(asyncTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        completeUnknown(jobId, url, throwable);
                    } else {
                        completeJob(jobId, result);
                    }
                });

        return jobId;
    }

    public UrlScanAsyncStatusResponse getAsyncScanStatus(String jobId) {
        cleanupExpiredAsyncJobs();
        AsyncScanJob job = asyncJobs.get(jobId);
        if (job == null) {
            throw new ResponseStatusException(NOT_FOUND, "Async scan job not found");
        }
        return new UrlScanAsyncStatusResponse(job.jobId, job.status, job.result, job.errorMessage);
    }

    @Scheduled(fixedDelayString = "${url.scan.async.cleanup-interval-ms:60000}")
    public void cleanupExpiredAsyncJobs() {
        long retentionMs = Math.max(0, asyncJobRetentionSeconds) * 1000;
        long now = System.currentTimeMillis();

        asyncJobs.entrySet().removeIf(entry -> {
            AsyncScanJob job = entry.getValue();
            if (job.isPending()) {
                return false;
            }
            if (retentionMs == 0) {
                return true;
            }
            return job.completedAtMs > 0 && (now - job.completedAtMs) >= retentionMs;
        });
    }

    public void clearScanCache() {
        urlScanCacheService.clear();
    }

    private AggregatedSignals evaluateAllHops(List<String> chain, boolean potentialHomograph) {
        AggregatedSignals signals = new AggregatedSignals();
        if (potentialHomograph) {
            signals.reasons.add("Potential homograph/punycode domain detected");
            signals.suspiciousScore += 20;
        }

        for (String hop : chain) {
            UrlNormalizationService.NormalizedUrl hopNormalized = urlNormalizationService.normalizeAndValidate(hop);
            URI uri = hopNormalized.uri();
            ssrfProtectionService.assertPublicDestination(uri);

            GoogleSafeBrowsingResult gsb = checkGoogleSafeBrowsing(hopNormalized.normalizedUrl());
            VirusTotalResult vt = checkVirusTotal(hopNormalized.normalizedUrl());

            signals.checksPerformed.add("Google Safe Browsing checked");
            signals.checksPerformed.add(vt.checked ? "VirusTotal checked" : "VirusTotal unavailable");

            if (gsb.externalError || !vt.checked) {
                signals.unknownSignals = true;
            }

            if (gsb.malicious) {
                signals.maliciousScore = Math.max(signals.maliciousScore, 95);
                signals.reasons.add("Google Safe Browsing detected malicious threat");
            }
            if (gsb.suspicious) {
                signals.suspiciousScore = Math.max(signals.suspiciousScore, 75);
                signals.reasons.add("Google Safe Browsing detected suspicious threat");
            }

            if (vt.maliciousEngines > 0) {
                signals.maliciousScore = Math.max(signals.maliciousScore, 90);
                signals.reasons.add("VirusTotal marked URL as malicious");
            } else if (vt.suspiciousEngines > 0) {
                signals.suspiciousScore = Math.max(signals.suspiciousScore, 65);
                signals.reasons.add("VirusTotal marked URL as suspicious");
            } else if (vt.analysisPending) {
                signals.unknownSignals = true;
                signals.reasons.add("VirusTotal analysis pending");
            }
        }

        if (chain.size() > 1) {
            signals.suspiciousScore += 10;
            signals.reasons.add("Redirect chain detected");
        }

        return signals;
    }

    private GoogleSafeBrowsingResult checkGoogleSafeBrowsing(String url) {
        if (safeBrowsingApiKey == null || safeBrowsingApiKey.isBlank()) {
            return GoogleSafeBrowsingResult.unavailable();
        }

        try {
            URI requestUri = UriComponentsBuilder
                    .fromUriString(safeBrowsingUrl)
                    .queryParam("key", safeBrowsingApiKey)
                    .build(true)
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(buildSafeBrowsingPayload(url), headers);

            ResponseEntity<String> response = retryService.execute(
                    () -> scannerRestTemplate.postForEntity(requestUri, requestEntity, String.class)
            );

            if (response.getBody() == null || response.getBody().isBlank()) {
                return new GoogleSafeBrowsingResult(false, false, false);
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode matches = root.path("matches");
            boolean malicious = false;
            boolean suspicious = false;
            if (matches.isArray()) {
                for (JsonNode match : matches) {
                    String threatType = match.path("threatType").asText("");
                    if ("MALWARE".equalsIgnoreCase(threatType)
                            || "POTENTIALLY_HARMFUL_APPLICATION".equalsIgnoreCase(threatType)) {
                        malicious = true;
                    }
                    if ("SOCIAL_ENGINEERING".equalsIgnoreCase(threatType)
                            || "UNWANTED_SOFTWARE".equalsIgnoreCase(threatType)) {
                        suspicious = true;
                    }
                }
            }
            return new GoogleSafeBrowsingResult(malicious, suspicious, false);
        } catch (Exception ex) {
            log.warn("Google Safe Browsing unavailable for {}", maskUrl(url));
            return GoogleSafeBrowsingResult.unavailable();
        }
    }

    private VirusTotalResult checkVirusTotal(String url) {
        if (virusTotalApiKey == null || virusTotalApiKey.isBlank()) {
            return VirusTotalResult.unavailable();
        }

        try {
            VirusTotalResult lookup = getVirusTotalUrlResult(url);
            if (lookup.checked || lookup.analysisPending) {
                return lookup;
            }

            String analysisId = submitVirusTotalAnalysis(url);
            if (analysisId == null || analysisId.isBlank()) {
                return VirusTotalResult.pending();
            }

            for (int attempt = 1; attempt <= Math.max(1, virusTotalPollMaxAttempts); attempt++) {
                VirusTotalResult polled = pollVirusTotalAnalysis(analysisId);
                if (polled.checked) {
                    return polled;
                }
                sleep(virusTotalPollDelayMs);
            }

            return VirusTotalResult.pending();
        } catch (Exception ex) {
            log.warn("VirusTotal unavailable for {}", maskUrl(url));
            return VirusTotalResult.unavailable();
        }
    }

    private VirusTotalResult getVirusTotalUrlResult(String url) throws Exception {
        URI lookupUri = UriComponentsBuilder
                .fromUriString(virusTotalUrl)
                .pathSegment(encodeUrlForVirusTotal(url))
                .build(true)
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.add("x-apikey", virusTotalApiKey);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = retryService.execute(
                    () -> scannerRestTemplate.exchange(lookupUri, HttpMethod.GET, request, String.class)
            );
            return parseVirusTotalStats(response.getBody());
        } catch (HttpClientErrorException.NotFound ex) {
            return VirusTotalResult.notFound();
        }
    }

    private String submitVirusTotalAnalysis(String url) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-apikey", virusTotalApiKey);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "url=" + UriComponentsBuilder.newInstance().queryParam("url", url).build().getQueryParams().getFirst("url");
        HttpEntity<String> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = retryService.execute(
                () -> scannerRestTemplate.postForEntity(virusTotalUrl, request, String.class)
        );
        if (response.getBody() == null || response.getBody().isBlank()) {
            return null;
        }
        JsonNode root = objectMapper.readTree(response.getBody());
        return root.path("data").path("id").asText(null);
    }

    private VirusTotalResult pollVirusTotalAnalysis(String analysisId) throws Exception {
        String resultUrl = String.format(virusTotalAnalysisUrl, analysisId);
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-apikey", virusTotalApiKey);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = retryService.execute(
                () -> scannerRestTemplate.exchange(resultUrl, HttpMethod.GET, request, String.class)
        );

        if (response.getBody() == null || response.getBody().isBlank()) {
            return VirusTotalResult.pending();
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        String status = root.path("data").path("attributes").path("status").asText("");
        if (!"completed".equalsIgnoreCase(status)) {
            return VirusTotalResult.pending();
        }

        JsonNode stats = root.path("data").path("attributes").path("stats");
        return VirusTotalResult.checked(stats.path("malicious").asInt(0), stats.path("suspicious").asInt(0));
    }

    private VirusTotalResult parseVirusTotalStats(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return VirusTotalResult.pending();
        }

        JsonNode root = objectMapper.readTree(body);
        JsonNode stats = root.path("data").path("attributes").path("last_analysis_stats");
        return VirusTotalResult.checked(stats.path("malicious").asInt(0), stats.path("suspicious").asInt(0));
    }

    private UrlscanBehavior scanWithUrlScan(String safeUrl) {
        try {
            String uuid = submitUrlscan(safeUrl);
            if (uuid == null || uuid.isBlank()) {
                return UrlscanBehavior.externalFailure(safeUrl);
            }
            return pollUrlscanResult(uuid, safeUrl);
        } catch (Exception ex) {
            log.warn("urlscan unavailable for {}", maskUrl(safeUrl));
            return UrlscanBehavior.externalFailure(safeUrl);
        }
    }

    private String submitUrlscan(String safeUrl) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("url", safeUrl);
        payload.put("visibility", "private");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("API-Key", urlscanApiKey);

        ResponseEntity<String> response = retryService.execute(
                () -> scannerRestTemplate.postForEntity(urlscanSubmitUrl, new HttpEntity<>(payload, headers), String.class)
        );

        if (response.getBody() == null || response.getBody().isBlank()) {
            return null;
        }
        JsonNode root = objectMapper.readTree(response.getBody());
        return root.path("uuid").asText(null);
    }

    private UrlscanBehavior pollUrlscanResult(String uuid, String fallbackUrl) {
        for (int attempt = 1; attempt <= Math.max(1, urlscanPollMaxAttempts); attempt++) {
            try {
                String resultUrl = String.format(urlscanResultUrlTemplate, uuid);
                HttpHeaders headers = new HttpHeaders();
                headers.add("API-Key", urlscanApiKey);

                ResponseEntity<String> response = retryService.execute(
                        () -> scannerRestTemplate.exchange(resultUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class)
                );

                if (response.getBody() != null && !response.getBody().isBlank()) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    return parseUrlscanResult(root, fallbackUrl);
                }
            } catch (HttpClientErrorException.NotFound ex) {
                // Result not ready yet.
            } catch (Exception ex) {
                return UrlscanBehavior.externalFailure(fallbackUrl);
            }
            sleep(urlscanPollDelayMs);
        }
        return UrlscanBehavior.timeoutFailure(fallbackUrl);
    }

    private UrlscanBehavior parseUrlscanResult(JsonNode root, String fallbackUrl) {
        UrlscanBehavior behavior = UrlscanBehavior.empty(fallbackUrl);

        String finalUrl = root.path("page").path("url").asText(fallbackUrl);
        behavior.finalUrl = finalUrl;

        JsonNode domainsNode = root.path("lists").path("domains");
        if (domainsNode.isArray()) {
            for (JsonNode node : domainsNode) {
                String value = node.asText("").trim();
                if (!value.isBlank()) {
                    behavior.contactedDomains.add(value.toLowerCase());
                }
            }
        }

        JsonNode scriptsNode = root.path("lists").path("scripts");
        behavior.scriptCount = scriptsNode.isArray() ? scriptsNode.size() : 0;
        behavior.totalRequests = root.path("stats").path("requests").asInt(0);
        behavior.pageTitle = root.path("page").path("title").asText("");
        behavior.screenshotUrl = root.path("task").path("screenshotURL").asText("");

        return behavior;
    }

    private Map<String, Object> buildSafeBrowsingPayload(String url) {
        Map<String, Object> client = new LinkedHashMap<>();
        client.put("clientId", "url-shortener");
        client.put("clientVersion", "2.0");

        Map<String, Object> threatEntry = new LinkedHashMap<>();
        threatEntry.put("url", url);

        Map<String, Object> threatInfo = new LinkedHashMap<>();
        threatInfo.put("threatTypes", List.of("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"));
        threatInfo.put("platformTypes", List.of("ANY_PLATFORM"));
        threatInfo.put("threatEntryTypes", List.of("URL"));
        threatInfo.put("threatEntries", List.of(threatEntry));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("client", client);
        payload.put("threatInfo", threatInfo);
        return payload;
    }

    private int calculateScore(AggregatedSignals signals) {
        int score = Math.max(signals.maliciousScore, signals.suspiciousScore);
        if (signals.unknownSignals) {
            score = Math.max(score, 40);
        }
        return Math.min(100, Math.max(0, score));
    }

    private String determineStatus(AggregatedSignals signals, int score) {
        if (signals.maliciousScore >= 90 || score >= 90) {
            return "MALICIOUS";
        }
        if (signals.unknownSignals && signals.maliciousScore < 90) {
            return "UNKNOWN";
        }
        if (signals.suspiciousScore >= 50 || score >= 50) {
            return "SUSPICIOUS";
        }
        return "SAFE";
    }

    private String buildMessage(String status) {
        return switch (status) {
            case "MALICIOUS" -> "Malicious indicators confirmed";
            case "SUSPICIOUS" -> "Suspicious indicators detected";
            case "UNKNOWN" -> "Scan could not be completed with high confidence";
            default -> "No malicious indicators found";
        };
    }

    private Map<String, Integer> buildBreakdown(AggregatedSignals signals) {
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        breakdown.put("malicious", signals.maliciousScore);
        breakdown.put("suspicious", signals.suspiciousScore);
        breakdown.put("unknown", signals.unknownSignals ? 100 : 0);
        return breakdown;
    }

    private Map<String, String> buildCategoryLabels(AggregatedSignals signals, int finalScore) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("malicious", label(signals.maliciousScore));
        labels.put("suspicious", label(signals.suspiciousScore));
        labels.put("overall", label(finalScore));
        labels.put("confidence", signals.unknownSignals ? "LOW" : "HIGH");
        return labels;
    }

    private String label(int score) {
        if (score >= 90) {
            return "HIGH";
        }
        if (score >= 50) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private void completeUnknown(String jobId, String inputUrl, Throwable throwable) {
        Throwable root = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;

        String normalized;
        try {
            normalized = urlNormalizationService.normalizeAndValidate(inputUrl).normalizedUrl();
        } catch (Exception ex) {
            normalized = inputUrl;
        }

        UrlScanResponse unknown = new UrlScanResponse(
                "UNKNOWN",
                "Async scan timed out or failed",
                normalized,
                40,
                Map.of("malicious", 0, "suspicious", 40, "unknown", 100),
                Map.of("overall", "MEDIUM", "confidence", "LOW"),
                List.of("Async scan failed"),
                List.of("Fallback status is UNKNOWN"),
                List.of(normalized),
                normalized,
                List.of(),
                0,
                "",
                ""
        );
        completeJob(jobId, unknown);
        log.warn("Async scan failed for {}: {}", maskUrl(normalized), root.getMessage());
    }

    private void completeJob(String jobId, UrlScanResponse response) {
        AsyncScanJob job = asyncJobs.get(jobId);
        if (job == null) {
            return;
        }

        synchronized (job) {
            if (!job.isPending()) {
                return;
            }
            job.status = "COMPLETED";
            job.result = response;
            job.errorMessage = null;
            job.completedAtMs = System.currentTimeMillis();
        }
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(Math.max(50, delayMs));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String encodeUrlForVirusTotal(String url) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(url.getBytes(StandardCharsets.UTF_8));
    }

    private String maskUrl(String url) {
        try {
            URI uri = URI.create(url);
            StringBuilder sb = new StringBuilder();
            sb.append(uri.getScheme()).append("://").append(uri.getHost());
            if (uri.getPort() > 0) {
                sb.append(":").append(uri.getPort());
            }
            sb.append(uri.getPath() == null ? "" : uri.getPath());
            return sb.toString();
        } catch (Exception ex) {
            return "[invalid-url]";
        }
    }

    private List<String> dedupe(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private static class AggregatedSignals {
        private int maliciousScore;
        private int suspiciousScore;
        private boolean unknownSignals;
        private final List<String> checksPerformed = new ArrayList<>();
        private final List<String> reasons = new ArrayList<>();
    }

    private static class GoogleSafeBrowsingResult {
        private final boolean malicious;
        private final boolean suspicious;
        private final boolean externalError;

        private GoogleSafeBrowsingResult(boolean malicious, boolean suspicious, boolean externalError) {
            this.malicious = malicious;
            this.suspicious = suspicious;
            this.externalError = externalError;
        }

        private static GoogleSafeBrowsingResult unavailable() {
            return new GoogleSafeBrowsingResult(false, false, true);
        }
    }

    private static class VirusTotalResult {
        private final int maliciousEngines;
        private final int suspiciousEngines;
        private final boolean checked;
        private final boolean analysisPending;

        private VirusTotalResult(int maliciousEngines, int suspiciousEngines, boolean checked, boolean analysisPending) {
            this.maliciousEngines = maliciousEngines;
            this.suspiciousEngines = suspiciousEngines;
            this.checked = checked;
            this.analysisPending = analysisPending;
        }

        private static VirusTotalResult checked(int malicious, int suspicious) {
            return new VirusTotalResult(malicious, suspicious, true, false);
        }

        private static VirusTotalResult notFound() {
            return new VirusTotalResult(0, 0, false, false);
        }

        private static VirusTotalResult pending() {
            return new VirusTotalResult(0, 0, false, true);
        }

        private static VirusTotalResult unavailable() {
            return new VirusTotalResult(0, 0, false, true);
        }
    }

    private static class UrlscanBehavior {
        private String finalUrl;
        private List<String> contactedDomains;
        private int scriptCount;
        private int totalRequests;
        private String pageTitle;
        private String screenshotUrl;
        private boolean externalError;

        private static UrlscanBehavior empty(String url) {
            UrlscanBehavior behavior = new UrlscanBehavior();
            behavior.finalUrl = url;
            behavior.contactedDomains = new ArrayList<>();
            behavior.scriptCount = 0;
            behavior.totalRequests = 0;
            behavior.pageTitle = "";
            behavior.screenshotUrl = "";
            behavior.externalError = false;
            return behavior;
        }

        private static UrlscanBehavior externalFailure(String url) {
            UrlscanBehavior behavior = empty(url);
            behavior.externalError = true;
            return behavior;
        }

        private static UrlscanBehavior timeoutFailure(String url) {
            UrlscanBehavior behavior = empty(url);
            behavior.externalError = true;
            return behavior;
        }
    }

    private static class AsyncScanJob {
        private final String jobId;
        private volatile String status;
        private volatile UrlScanResponse result;
        private volatile String errorMessage;
        private volatile long completedAtMs;

        private AsyncScanJob(String jobId) {
            this.jobId = jobId;
            this.status = "PENDING";
            this.result = null;
            this.errorMessage = null;
            this.completedAtMs = 0;
        }

        private boolean isPending() {
            return "PENDING".equals(this.status);
        }
    }
}
