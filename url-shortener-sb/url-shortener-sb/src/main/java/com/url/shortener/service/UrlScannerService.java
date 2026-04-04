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
import org.springframework.beans.factory.annotation.Autowired;
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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

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

    @Value("${urlscan.wait-ms:5000}")
    private long urlscanWaitMs;

    @Value("${url.scan.async.timeout-ms:20000}")
    private long asyncTimeoutMs;

    @Value("${url.scan.async.job-retention-seconds:900}")
    private long asyncJobRetentionSeconds;

    @Value("${url.scan.async.max-jobs:200}")
    private int maxAsyncJobs;

    @Value("${url.scan.suspicious-tlds:xyz,tk,top,click,gq}")
    private String suspiciousTldsCsv;

    @Value("${url.scan.piracy-keywords:torrent,watch free,movierulz,123movies}")
    private String piracyKeywordsCsv;

    private final ObjectMapper objectMapper;
    private final RestTemplate scannerRestTemplate;
    private final UrlResolverService urlResolverService;
    private final UrlScanCacheService urlScanCacheService;
    private final UrlScanResultService urlScanResultService;
    private final UrlNormalizationService urlNormalizationService;
    private final SsrfProtectionService ssrfProtectionService;
    private final HttpRetryService retryService;

    @Autowired
    @Qualifier("scanTaskExecutor")
    private Executor scanTaskExecutor;

    private final Map<String, AsyncScanJob> asyncJobs = new ConcurrentHashMap<>();

    public UrlScanResponse scanUrl(String url) {
        return scanUrl(url, null);
    }

    public UrlScanResponse scanUrl(String url, String ipAddress) {
        // STEP 1: normalize URL
        UrlNormalizationService.NormalizedUrl normalized = urlNormalizationService.normalizeAndValidate(url);
        log.debug("Pipeline step1 normalize complete: {}", maskUrl(normalized.normalizedUrl()));
        boolean internalInput = urlResolverService.isInternalShortUrl(normalized.normalizedUrl());
        log.info("Scan input classification: url={}, type={}", maskUrl(normalized.normalizedUrl()), internalInput ? "internal" : "external");
        System.out.println("SCAN REQUEST RECEIVED: " + normalized.normalizedUrl());

        // DB-first cache check to avoid repeated third-party scanner calls.
        Optional<UrlScanResponse> existingFromDb = urlScanResultService.findByScannedUrl(normalized.normalizedUrl());
        if (existingFromDb.isPresent()) {
            UrlScanResponse existing = existingFromDb.get();
            log.info("DB cache hit for {} with status={}", maskUrl(normalized.normalizedUrl()), existing.getStatus());
            log.info("RETURNING TO FRONTEND (DB cache): status={}, scannedUrl={}", existing.getStatus(), maskUrl(normalized.normalizedUrl()));
            if (isCacheableResult(existing)) {
                urlScanCacheService.put(normalized.normalizedUrl(), existing);
            }
            return existing;
        }
        log.debug("DB cache miss for {}", maskUrl(normalized.normalizedUrl()));

        UrlScanResponse cached = urlScanCacheService.get(normalized.normalizedUrl());
        if (isCacheableResult(cached)) {
            log.debug("Cache hit for {}", maskUrl(normalized.normalizedUrl()));
            log.info("RETURNING TO FRONTEND (memory cache): status={}, scannedUrl={}", cached.getStatus(), maskUrl(normalized.normalizedUrl()));
            return cached;
        } else if (cached != null) {
            log.debug("Ignoring non-cacheable cached result for {} with status={}", maskUrl(normalized.normalizedUrl()), cached.getStatus());
        }

        // STEP 2: resolve redirects
        UrlResolverService.ResolvedResult resolved = urlResolverService.resolveUrl(normalized.normalizedUrl());
        List<String> chain = resolved.chain() == null || resolved.chain().isEmpty()
                ? List.of(normalized.normalizedUrl())
                : resolved.chain();
        String finalUrl = chain.get(chain.size() - 1);
        log.debug("Pipeline step2 resolve complete: chainSize={}, finalUrl={}", chain.size(), maskUrl(finalUrl));
        log.info("Scan resolved final URL: input={}, final={}", maskUrl(normalized.normalizedUrl()), maskUrl(finalUrl));
        log.info("Scan resolve strategy: input={} strategy={}", maskUrl(normalized.normalizedUrl()), internalInput ? "INTERNAL_DB" : "EXTERNAL_HTTP");
        System.out.println("Resolved URL TYPE=" + (internalInput ? "internal" : "external") + " FINAL=" + finalUrl);

        // STEP 5: urlscan can run in parallel while GSB/VT execute.
        CompletableFuture<UrlscanBehavior> urlscanFuture = CompletableFuture.supplyAsync(
                () -> shouldRunUrlscan() ? scanWithUrlScan(urlNormalizationService.normalizeAndValidate(finalUrl).urlscanSafeUrl()) : UrlscanBehavior.skipped(finalUrl),
                scanTaskExecutor
        );

        // STEP 3 + 4: always run GSB + VT for each hop.
        ScoreState state = evaluateCoreSignals(chain, normalized.potentialHomograph(), resolved);

        UrlscanBehavior behavior;
        try {
            // Do not return early; wait until urlscan returns data or its own poll timeout.
            behavior = urlscanFuture.join();
        } catch (CompletionException ex) {
            behavior = UrlscanBehavior.externalFailure(finalUrl);
        }
        applyUrlscanSignals(state, behavior);

        int finalScore = calculateFinalScore(state);
        String verdict = determineVerdict(finalScore, state);

        UrlScanResponse response = buildResponse(normalized.normalizedUrl(), finalUrl, chain, behavior, state, finalScore, verdict);

        try {
            urlScanResultService.saveScanResult(response, ipAddress);
        } catch (Exception ex) {
            log.error("Continuing despite DB persistence failure for {}", maskUrl(normalized.normalizedUrl()), ex);
        }

        if (isCacheableResult(response)) {
            urlScanCacheService.put(normalized.normalizedUrl(), response);
        }

        log.info("Scan completed: url={}, verdict={}, score={}", maskUrl(normalized.normalizedUrl()), verdict, finalScore);
        log.debug("Category scores malware={}, phishing={}, spam={}, piracy={}, redirect={}, domain={}",
                state.malwareScore, state.phishingScore, state.spamScore, state.piracyScore, state.redirectScore, state.domainScore);
        log.info("RETURNING TO FRONTEND: status={}, finalUrl={}, screenshotUrl={}",
                response.getStatus(), maskUrl(response.getFinalUrl()), response.getScreenshotUrl());
        return response;
    }

    private ScoreState evaluateCoreSignals(List<String> chain, boolean potentialHomograph, UrlResolverService.ResolvedResult resolved) {
        ScoreState state = new ScoreState();

        if (potentialHomograph) {
            state.domainScore = Math.max(state.domainScore, 25);
            state.reasons.add("Potential homograph/punycode domain detected");
        }

        if (resolved.loopDetected()) {
            state.redirectScore = Math.max(state.redirectScore, 30);
            state.reasons.add("Redirect loop detected");
        }
        if (resolved.maxDepthReached()) {
            state.redirectScore = Math.max(state.redirectScore, 25);
            state.reasons.add("Redirect depth limit reached");
        }

        if (chain.size() > 1) {
            state.redirectScore = Math.max(state.redirectScore, Math.min(30, 10 + ((chain.size() - 1) * 5)));
            state.reasons.add("Redirect chain length > 1");
        }
        if (hasDomainChange(chain)) {
            state.redirectScore = Math.max(state.redirectScore, Math.min(40, state.redirectScore + 10));
            state.reasons.add("Domain changed across redirects");
        }

        for (String hop : chain) {
            UrlNormalizationService.NormalizedUrl normalizedHop = urlNormalizationService.normalizeAndValidate(hop);
            if (urlResolverService.isInternalShortUrl(normalizedHop.normalizedUrl())) {
                state.checksPerformed.add("Internal short URL resolved from database");
                log.debug("Skipping SSRF/API checks for internal hop {}", maskUrl(normalizedHop.normalizedUrl()));
                continue;
            }

            URI hopUri = normalizedHop.uri();
            ssrfProtectionService.assertPublicDestination(hopUri);

            String domain = extractDomain(normalizedHop.normalizedUrl());
            applyDomainHeuristics(state, domain, normalizedHop.normalizedUrl());
            applyPiracyHeuristics(state, domain, normalizedHop.normalizedUrl());
            applySpamHeuristics(state, normalizedHop.normalizedUrl());

            log.debug("Pipeline step3 calling GSB for {}", maskUrl(normalizedHop.normalizedUrl()));
            GoogleSafeBrowsingResult gsb = checkGoogleSafeBrowsing(normalizedHop.normalizedUrl());
            state.checksPerformed.add("Google Safe Browsing checked");
            log.debug("GSB result for {} => malware={}, phishing={}, externalError={}", maskUrl(normalizedHop.normalizedUrl()), gsb.malware, gsb.phishing, gsb.externalError);

            if (gsb.externalError) {
                state.gsbFailed = true;
                state.apiFailureSignals = true;
                state.reasons.add("Google Safe Browsing unavailable");
            } else {
                state.gsbResponded = true;
            }
            if (gsb.malware) {
                state.malwareScore = Math.max(state.malwareScore, 95);
                state.reasons.add("Google Safe Browsing flagged malware");
            }
            if (gsb.phishing) {
                state.phishingScore = Math.max(state.phishingScore, 90);
                state.reasons.add("Google Safe Browsing flagged phishing");
            }

            log.debug("Pipeline step4 calling VirusTotal for {}", maskUrl(normalizedHop.normalizedUrl()));
            VirusTotalResult vt = checkVirusTotal(normalizedHop.normalizedUrl());
            state.checksPerformed.add(vt.checked ? "VirusTotal checked" : "VirusTotal partial/unavailable");
            log.debug("VirusTotal result for {} => malicious={}, suspicious={}, checked={}, pending={}",
                    maskUrl(normalizedHop.normalizedUrl()), vt.maliciousEngines, vt.suspiciousEngines, vt.checked, vt.analysisPending);

            if (vt.externalError) {
                state.vtFailed = true;
                state.apiFailureSignals = true;
                state.reasons.add("VirusTotal unavailable");
            } else {
                state.vtResponded = true;
                if (!vt.checked) {
                    state.reasons.add("VirusTotal has no prior data yet; treated as neutral");
                }
            }
            if (vt.maliciousEngines > 0) {
                state.malwareScore = Math.max(state.malwareScore, 90);
                state.phishingScore = Math.max(state.phishingScore, 80);
                state.reasons.add("VirusTotal flagged URL as malicious");
            }
            if (vt.suspiciousEngines > 0) {
                state.spamScore = Math.max(state.spamScore, 60);
                state.phishingScore = Math.max(state.phishingScore, 70);
                state.reasons.add("VirusTotal flagged as suspicious");
            }
        }

        return state;
    }

    private void applyUrlscanSignals(ScoreState state, UrlscanBehavior behavior) {
        if (behavior == null) {
            state.checksPerformed.add("urlscan skipped");
            return;
        }

        if ("SKIPPED".equals(behavior.scanStatus)) {
            state.checksPerformed.add("urlscan skipped");
            return;
        }

        state.checksPerformed.add("urlscan checked");

        if (behavior.externalError) {
            state.reasons.add("urlscan unavailable");
            return;
        }

        if (behavior.scriptCount > 25) {
            state.spamScore = Math.max(state.spamScore, 55);
            state.reasons.add("High script activity detected via urlscan");
        }
        if (behavior.contactedDomains.size() > 15) {
            state.spamScore = Math.max(state.spamScore, 50);
            state.reasons.add("High contacted domain count via urlscan");
        }
        if (behavior.pageTitle != null && containsAny(behavior.pageTitle.toLowerCase(), piracyKeywordsCsv.toLowerCase())) {
            state.piracyScore = Math.max(state.piracyScore, 70);
            state.reasons.add("Piracy keyword found in page title");
        }
        if (behavior.screenshotUrl != null && !behavior.screenshotUrl.isBlank()) {
            state.reasons.add("urlscan screenshot captured");
        }
    }

    private void applyDomainHeuristics(ScoreState state, String domain, String url) {
        if (domain.isBlank()) {
            return;
        }
        String tld = extractTld(domain);
        if (containsAny(tld, suspiciousTldsCsv)) {
            state.domainScore = Math.max(state.domainScore, 25);
            state.reasons.add("Suspicious TLD detected");
        }
        if (hasDigitSubstitution(domain)) {
            state.domainScore = Math.max(state.domainScore, 20);
            state.reasons.add("Suspicious domain digit substitution");
        }
        if (url.length() > 180) {
            state.domainScore = Math.max(state.domainScore, 20);
            state.reasons.add("Very long URL detected");
        }
    }

    private void applyPiracyHeuristics(ScoreState state, String domain, String url) {
        String lowerDomain = domain == null ? "" : domain.toLowerCase();
        String lowerUrl = url == null ? "" : url.toLowerCase();

        if (lowerDomain.contains("torrent") || lowerDomain.contains("movierulz") || lowerDomain.contains("123movies")) {
            state.piracyScore = Math.max(state.piracyScore, 80);
            state.reasons.add("Piracy domain pattern detected");
        }

        if (containsAny(lowerUrl, piracyKeywordsCsv.toLowerCase())) {
            state.piracyScore = Math.max(state.piracyScore, 65);
            state.reasons.add("Piracy keyword detected in URL");
        }
    }

    private void applySpamHeuristics(ScoreState state, String url) {
        if (url == null) {
            return;
        }
        int queryCount = countQueryParams(url);
        if (queryCount >= 4) {
            state.spamScore = Math.max(state.spamScore, Math.min(70, 30 + (queryCount * 5)));
            state.reasons.add("High query-parameter count detected");
        }
        String lower = url.toLowerCase();
        if (lower.contains("redirect=") || lower.contains("utm_") || lower.contains("click")) {
            state.spamScore = Math.max(state.spamScore, 45);
            state.reasons.add("Tracking/redirect URL pattern detected");
        }
    }

    private int calculateFinalScore(ScoreState state) {
        double weighted = (state.malwareScore * 0.30)
                + (state.phishingScore * 0.25)
                + (state.spamScore * 0.15)
                + (state.piracyScore * 0.10)
                + (state.redirectScore * 0.10)
                + (state.domainScore * 0.10);
        int score = (int) Math.round(weighted);
        return Math.max(0, Math.min(100, score));
    }

    private String determineVerdict(int finalScore, ScoreState state) {
        boolean coreApisFailed = state.gsbFailed && state.vtFailed;
        if (coreApisFailed) {
            log.debug("Verdict UNKNOWN because core APIs failed: gsbFailed={}, vtFailed={}", state.gsbFailed, state.vtFailed);
            return "UNKNOWN";
        }
        if (finalScore > 70) {
            log.debug("Verdict MALICIOUS at score={} (gsbResponded={}, vtResponded={})", finalScore, state.gsbResponded, state.vtResponded);
            return "MALICIOUS";
        }
        if (finalScore >= 30) {
            log.debug("Verdict SUSPICIOUS at score={} (gsbResponded={}, vtResponded={})", finalScore, state.gsbResponded, state.vtResponded);
            return "SUSPICIOUS";
        }
        log.debug("Verdict SAFE at score={} (gsbResponded={}, vtResponded={})", finalScore, state.gsbResponded, state.vtResponded);
        return "SAFE";
    }

    private boolean isCacheableResult(UrlScanResponse response) {
        if (response == null || response.getStatus() == null) {
            return false;
        }
        String status = response.getStatus().toUpperCase();
        if (!("SAFE".equals(status) || "SUSPICIOUS".equals(status) || "MALICIOUS".equals(status))) {
            return false;
        }
        return response.getFinalScore() >= 0 && response.getScannedUrl() != null && !response.getScannedUrl().isBlank();
    }

    private UrlScanResponse buildResponse(String scannedUrl,
                                          String finalUrl,
                                          List<String> chain,
                                          UrlscanBehavior behavior,
                                          ScoreState state,
                                          int finalScore,
                                          String verdict) {
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        breakdown.put("malware", state.malwareScore);
        breakdown.put("phishing", state.phishingScore);
        breakdown.put("spam", state.spamScore);
        breakdown.put("piracy", state.piracyScore);
        breakdown.put("redirect", state.redirectScore);
        breakdown.put("domain", state.domainScore);

        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("malware", level(state.malwareScore));
        labels.put("phishing", level(state.phishingScore));
        labels.put("spam", level(state.spamScore));
        labels.put("piracy", level(state.piracyScore));
        labels.put("redirect", level(state.redirectScore));
        labels.put("domain", level(state.domainScore));

        UrlScanResponse response = new UrlScanResponse(
                verdict,
                messageFor(verdict),
                scannedUrl,
                finalScore,
                breakdown,
                labels,
                dedupe(state.checksPerformed),
                dedupe(state.reasons),
                chain,
                behavior != null && behavior.finalUrl != null && !behavior.finalUrl.isBlank() ? behavior.finalUrl : finalUrl,
                behavior == null ? List.of() : behavior.contactedDomains,
                behavior == null ? 0 : behavior.scriptCount,
                behavior == null ? "" : behavior.pageTitle,
                behavior == null ? "" : behavior.screenshotUrl
        );

        response.setVerdict(verdict);
        response.setMalwareScore(state.malwareScore);
        response.setPhishingScore(state.phishingScore);
        response.setSpamScore(state.spamScore);
        response.setPiracyScore(state.piracyScore);
        response.setRedirectScore(state.redirectScore);
        response.setDomainScore(state.domainScore);
        response.setTotalRequests(behavior == null ? 0 : behavior.totalRequests);
        response.setUrlscanScanId(behavior == null ? "" : behavior.urlscanScanId);
        response.setResultUrl(behavior == null ? "" : behavior.resultUrl);
        return response;
    }

    private String messageFor(String verdict) {
        return switch (verdict) {
            case "MALICIOUS" -> "High-confidence malicious indicators detected";
            case "SUSPICIOUS" -> "Suspicious indicators detected";
            case "UNKNOWN" -> "Unable to fully verify due to provider failures";
            default -> "No high-risk signals detected";
        };
    }

    private String level(int score) {
        if (score > 70) {
            return "HIGH";
        }
        if (score >= 30) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private boolean shouldRunUrlscan() {
        return urlscanApiKey != null && !urlscanApiKey.isBlank();
    }

    public String submitAsyncScan(String url) {
        return submitAsyncScan(url, null);
    }

    public String submitAsyncScan(String url, String ipAddress) {
        cleanupExpiredAsyncJobs();
        long pendingJobs = asyncJobs.values().stream().filter(AsyncScanJob::isPending).count();
        if (pendingJobs >= Math.max(1, maxAsyncJobs)) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "Too many async scan jobs");
        }

        String scanId = UUID.randomUUID().toString();
        AsyncScanJob job = new AsyncScanJob(scanId, url, ipAddress);
        asyncJobs.put(scanId, job);

        CompletableFuture
                .supplyAsync(() -> scanUrl(url, ipAddress), scanTaskExecutor)
                .orTimeout(asyncTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        completeUnknown(scanId, url, ipAddress, throwable);
                    } else {
                        completeJob(scanId, result);
                    }
                });

        return scanId;
    }

    public UrlScanAsyncStatusResponse getAsyncScanStatus(String scanId) {
        cleanupExpiredAsyncJobs();
        AsyncScanJob job = asyncJobs.get(scanId);
        if (job == null) {
            throw new ResponseStatusException(NOT_FOUND, "Async scan job not found");
        }

        if (job.isPending() && isJobTimedOut(job)) {
            log.warn("Auto-finalizing stale scan job {} after timeout window", scanId);
            completeUnknown(scanId, job.requestedUrl, job.requestedIp, new TimeoutException("Async scan exceeded timeout window"));
            job = asyncJobs.get(scanId);
        }

        return new UrlScanAsyncStatusResponse(job.scanId, job.status, job.result, job.errorMessage);
    }

    private boolean isJobTimedOut(AsyncScanJob job) {
        long graceMs = 2000;
        long ageMs = System.currentTimeMillis() - job.createdAtMs;
        return ageMs >= Math.max(1000, asyncTimeoutMs + graceMs);
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
            boolean malware = false;
            boolean phishing = false;
            if (matches.isArray()) {
                for (JsonNode match : matches) {
                    String threatType = match.path("threatType").asText("");
                    if ("MALWARE".equalsIgnoreCase(threatType)
                            || "POTENTIALLY_HARMFUL_APPLICATION".equalsIgnoreCase(threatType)) {
                        malware = true;
                    }
                    if ("SOCIAL_ENGINEERING".equalsIgnoreCase(threatType)
                            || "UNWANTED_SOFTWARE".equalsIgnoreCase(threatType)) {
                        phishing = true;
                    }
                }
            }
            return new GoogleSafeBrowsingResult(malware, phishing, false);
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
            log.debug("Pipeline step5 calling urlscan submit for {}", maskUrl(safeUrl));
            String uuid = submitUrlscan(safeUrl);
            if (uuid == null || uuid.isBlank()) {
                return UrlscanBehavior.externalFailure(safeUrl);
            }
            UrlscanBehavior behavior = pollUrlscanResult(uuid, safeUrl);
            behavior.urlscanScanId = uuid;
            behavior.screenshotUrl = buildUrlscanScreenshotUrl(uuid);
            behavior.resultUrl = buildUrlscanResultUrl(uuid);
            log.info("SCREENSHOT URL GENERATED: {}", behavior.screenshotUrl);
            return behavior;
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
        String uuid = root.path("uuid").asText(null);
        log.info("URLSCAN UUID: {}", uuid);
        return uuid;
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
                    if (isUrlscanResultReady(root)) {
                        return parseUrlscanResult(root, fallbackUrl, uuid);
                    }
                }
            } catch (HttpClientErrorException.NotFound ex) {
                // Still processing.
            } catch (Exception ex) {
                return UrlscanBehavior.externalFailure(fallbackUrl);
            }
            sleep(urlscanPollDelayMs);
        }
        return UrlscanBehavior.timeoutFailure(fallbackUrl);
    }

    private boolean isUrlscanResultReady(JsonNode root) {
        String pageUrl = root.path("page").path("url").asText("");
        String taskUrl = root.path("task").path("url").asText("");
        return (!pageUrl.isBlank()) || (!taskUrl.isBlank());
    }

    private UrlscanBehavior parseUrlscanResult(JsonNode root, String fallbackUrl, String uuid) {
        UrlscanBehavior behavior = UrlscanBehavior.empty(fallbackUrl);
        behavior.urlscanScanId = uuid == null ? "" : uuid;
        behavior.resultUrl = buildUrlscanResultUrl(uuid);

        behavior.finalUrl = root.path("page").path("url").asText(fallbackUrl);

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

        behavior.screenshotUrl = buildUrlscanScreenshotUrl(uuid);
        behavior.scanStatus = "COMPLETED";
        return behavior;
    }

    private String buildUrlscanScreenshotUrl(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return "";
        }
        return "https://urlscan.io/screenshots/" + uuid + ".png";
    }

    private String buildUrlscanResultUrl(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return "";
        }
        return "https://urlscan.io/result/" + uuid + "/";
    }

    private Map<String, Object> buildSafeBrowsingPayload(String url) {
        Map<String, Object> client = new LinkedHashMap<>();
        client.put("clientId", "url-shortener");
        client.put("clientVersion", "3.0");

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

    private boolean hasDomainChange(List<String> chain) {
        Set<String> domains = new LinkedHashSet<>();
        for (String url : chain) {
            String domain = extractDomain(url);
            if (!domain.isBlank()) {
                domains.add(domain);
            }
        }
        return domains.size() > 1;
    }

    private String extractDomain(String url) {
        try {
            URL parsed = new URL(url);
            return parsed.getHost() == null ? "" : parsed.getHost().toLowerCase();
        } catch (Exception ex) {
            return "";
        }
    }

    private String extractTld(String domain) {
        if (domain == null || !domain.contains(".")) {
            return "";
        }
        String[] parts = domain.split("\\.");
        return parts[parts.length - 1].toLowerCase();
    }

    private boolean hasDigitSubstitution(String domain) {
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char ch : domain.toCharArray()) {
            if (Character.isLetter(ch)) {
                hasLetter = true;
            }
            if (Character.isDigit(ch)) {
                hasDigit = true;
            }
        }
        return hasLetter && hasDigit;
    }

    private boolean containsAny(String value, String csv) {
        if (value == null || csv == null) {
            return false;
        }
        for (String token : csv.split(",")) {
            String cleaned = token.trim().toLowerCase();
            if (!cleaned.isBlank() && value.toLowerCase().contains(cleaned)) {
                return true;
            }
        }
        return false;
    }

    private int countQueryParams(String url) {
        int question = url.indexOf('?');
        if (question < 0 || question == url.length() - 1) {
            return 0;
        }
        int count = 1;
        for (char ch : url.substring(question + 1).toCharArray()) {
            if (ch == '&') {
                count++;
            }
        }
        return count;
    }

    private void completeUnknown(String scanId, String inputUrl, String ipAddress, Throwable throwable) {
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
                "Scan failed due to provider timeout or processing error",
                normalized,
                0,
                Map.of("malware", 0, "phishing", 0, "spam", 0, "piracy", 0, "redirect", 0, "domain", 0),
                Map.of("malware", "LOW", "phishing", "LOW", "spam", "LOW", "piracy", "LOW", "redirect", "LOW", "domain", "LOW"),
                List.of("Async pipeline failed"),
                List.of("External provider timeout or failure"),
                List.of(normalized),
                normalized,
                List.of(),
                0,
                "",
                ""
        );
        unknown.setVerdict("UNKNOWN");

        try {
            UrlScanResponse fallback = scanUrl(inputUrl, ipAddress);
            if (fallback != null && !"UNKNOWN".equalsIgnoreCase(fallback.getStatus())) {
                List<String> reasons = fallback.getReasons() == null ? new ArrayList<>() : new ArrayList<>(fallback.getReasons());
                reasons.add("Async timeout recovered using fallback completion");
                fallback.setReasons(dedupe(reasons));
                completeJob(scanId, fallback);
                log.info("Async timeout recovered for {} with status={} score={}", maskUrl(normalized), fallback.getStatus(), fallback.getFinalScore());
                return;
            }
        } catch (Exception fallbackEx) {
            log.debug("Fallback completion after async timeout failed for {}: {}", maskUrl(normalized), fallbackEx.getMessage());
        }

        completeJob(scanId, unknown);
        log.warn("Async scan failed for {}: {}", maskUrl(normalized), root.getMessage());
    }

    private void completeJob(String scanId, UrlScanResponse response) {
        AsyncScanJob job = asyncJobs.get(scanId);
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
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPath() == null ? "" : uri.getPath());
        } catch (Exception ex) {
            return "[invalid-url]";
        }
    }

    private List<String> dedupe(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private static class ScoreState {
        private int malwareScore;
        private int phishingScore;
        private int spamScore;
        private int piracyScore;
        private int redirectScore;
        private int domainScore;
        private boolean apiFailureSignals;
        private boolean gsbResponded;
        private boolean gsbFailed;
        private boolean vtResponded;
        private boolean vtFailed;
        private final List<String> checksPerformed = new ArrayList<>();
        private final List<String> reasons = new ArrayList<>();
    }

    private static class GoogleSafeBrowsingResult {
        private final boolean malware;
        private final boolean phishing;
        private final boolean externalError;

        private GoogleSafeBrowsingResult(boolean malware, boolean phishing, boolean externalError) {
            this.malware = malware;
            this.phishing = phishing;
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
        private final boolean externalError;

        private VirusTotalResult(int maliciousEngines, int suspiciousEngines, boolean checked, boolean analysisPending, boolean externalError) {
            this.maliciousEngines = maliciousEngines;
            this.suspiciousEngines = suspiciousEngines;
            this.checked = checked;
            this.analysisPending = analysisPending;
            this.externalError = externalError;
        }

        private static VirusTotalResult checked(int malicious, int suspicious) {
            return new VirusTotalResult(malicious, suspicious, true, false, false);
        }

        private static VirusTotalResult notFound() {
            return new VirusTotalResult(0, 0, false, false, false);
        }

        private static VirusTotalResult pending() {
            return new VirusTotalResult(0, 0, false, true, false);
        }

        private static VirusTotalResult unavailable() {
            return new VirusTotalResult(0, 0, false, true, true);
        }
    }

    private static class UrlscanBehavior {
        private String urlscanScanId;
        private String resultUrl;
        private String finalUrl;
        private List<String> contactedDomains;
        private int scriptCount;
        private int totalRequests;
        private String pageTitle;
        private String screenshotUrl;
        private boolean externalError;
        private String scanStatus;

        private static UrlscanBehavior empty(String url) {
            UrlscanBehavior behavior = new UrlscanBehavior();
            behavior.urlscanScanId = "";
            behavior.resultUrl = "";
            behavior.finalUrl = url;
            behavior.contactedDomains = new ArrayList<>();
            behavior.scriptCount = 0;
            behavior.totalRequests = 0;
            behavior.pageTitle = "";
            behavior.screenshotUrl = "";
            behavior.externalError = false;
            behavior.scanStatus = "COMPLETED";
            return behavior;
        }

        private static UrlscanBehavior externalFailure(String url) {
            UrlscanBehavior behavior = empty(url);
            behavior.externalError = true;
            behavior.scanStatus = "FAILED";
            return behavior;
        }

        private static UrlscanBehavior timeoutFailure(String url) {
            UrlscanBehavior behavior = empty(url);
            behavior.externalError = true;
            behavior.scanStatus = "TIMEOUT";
            return behavior;
        }

        private static UrlscanBehavior skipped(String url) {
            UrlscanBehavior behavior = empty(url);
            behavior.scanStatus = "SKIPPED";
            return behavior;
        }
    }

    private static class AsyncScanJob {
        private final String scanId;
        private final String requestedUrl;
        private final String requestedIp;
        private final long createdAtMs;
        private volatile String status;
        private volatile UrlScanResponse result;
        private volatile String errorMessage;
        private volatile long completedAtMs;

        private AsyncScanJob(String scanId, String requestedUrl, String requestedIp) {
            this.scanId = scanId;
            this.requestedUrl = requestedUrl;
            this.requestedIp = requestedIp;
            this.createdAtMs = System.currentTimeMillis();
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
