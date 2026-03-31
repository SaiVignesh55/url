package com.url.shortener.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.url.shortener.dtos.UrlScanAsyncStatusResponse;
import com.url.shortener.dtos.UrlScanHistoryResponse;
import com.url.shortener.dtos.UrlScanResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    @Value("${url.scan.max-length:120}")
    private int maxUrlLength;

    @Value("${url.scan.suspicious-tlds:xyz,tk,top}")
    private String suspiciousTldsCsv;

    @Value("${url.scan.piracy-keywords:movie,download,watch free,torrent}")
    private String piracyKeywordsCsv;

    @Value("${url.scan.ad-keywords:ads,click,redirect,banner,track,tracker}")
    private String adKeywordsCsv;

    @Value("${url.scan.trusted-domains:facebook.com,google.com,twitter.com}")
    private String trustedDomainsCsv;

    @Value("${url.scan.async.timeout-ms:20000}")
    private long asyncTimeoutMs;

    @Value("${url.scan.async.job-retention-seconds:900}")
    private long asyncJobRetentionSeconds;

    @Value("${url.scan.async.max-jobs:200}")
    private int maxAsyncJobs;

    private final ObjectMapper objectMapper;
    private final UrlResolverService urlResolverService;
    private final UrlScanCacheService urlScanCacheService;

    private final Map<String, AsyncScanJob> asyncJobs = new ConcurrentHashMap<>();
    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService asyncScheduler = Executors.newSingleThreadScheduledExecutor();

    public UrlScanResponse scanUrl(String url) {
        return scanUrlInternal(url, false);
    }

    private UrlScanResponse scanUrlInternal(String url, boolean forceUrlscan) {
        String normalizedUrl = normalizeUrl(url);
        if (normalizedUrl.isBlank()) {
            return buildErrorResponse("INVALID_REQUEST", "URL is required", null,
                    List.of("Please provide a valid URL in request body"));
        }

        UrlResolverService.ResolvedResult resolvedResult = urlResolverService.resolveUrl(normalizedUrl);
        String resolvedFinalUrl = normalizeUrl(resolvedResult.finalUrl());
        if (resolvedFinalUrl.isBlank()) {
            resolvedFinalUrl = normalizedUrl;
        }

        UrlScanResponse cached = urlScanCacheService.get(resolvedFinalUrl);
        if (cached != null) {
            cached.setScannedUrl(normalizedUrl);
            cached.setFinalUrl(resolvedFinalUrl);
            return cached;
        }

        try {
            int malwareScore = 0;
            int phishingScore = 0;
            int piracyScore = 0;
            int spamScore = 0;
            int redirectScore = 0;
            int domainScore = 0;

            List<String> reasons = new ArrayList<>();
            List<String> checksPerformed = new ArrayList<>();

            List<String> redirectChain = resolvedResult.chain() == null
                    ? new ArrayList<>(List.of(normalizedUrl))
                    : new ArrayList<>(resolvedResult.chain());
            if (redirectChain.isEmpty()) {
                redirectChain.add(normalizedUrl);
            }

            String finalUrl = resolvedFinalUrl;
            String finalDomain = extractDomain(finalUrl);
            boolean trustedDomain = isTrustedDomain(finalDomain);
            boolean suspiciousDomain = isSuspiciousDomain(finalDomain);

            UrlscanBehavior behavior = UrlscanBehavior.empty(finalUrl);
            behavior.redirectChain = new ArrayList<>(redirectChain);
            behavior.finalUrl = finalUrl;
            checksPerformed.add("Redirect resolution completed");

            if (redirectChain.size() > 1) {
                redirectScore += 20;
                reasons.add("URL redirects detected");
            }
            if (isKnownShortener(extractDomain(normalizedUrl))) {
                reasons.add("Shortened URL detected");
            }
            if (hasDomainChangeAcrossRedirects(redirectChain)) {
                redirectScore += 20;
                reasons.add("Domain changed across redirects");
            }

            // STEP 1/2: Always call Google Safe Browsing and treat it as highest authority.
            GoogleSafeBrowsingResult gsb = checkGoogleSafeBrowsing(finalUrl);
            if (gsb.externalError) {
                checksPerformed.add("Google Safe Browsing check unavailable");
                reasons.add("Google Safe Browsing unavailable");
            } else if (gsb.hasAnyThreat()) {
                checksPerformed.add("Google Safe Browsing check completed");
                if (gsb.malwareThreat) {
                    malwareScore += 100;
                }
                if (gsb.phishingThreat) {
                    phishingScore += 90;
                }
                reasons.add("Google Safe Browsing detected threat");
            } else {
                checksPerformed.add("Google Safe Browsing check completed");
                reasons.add("No malware detected by Google Safe Browsing");
            }

            // STEP 3: urlscan behavior engine, called conditionally.
            boolean shouldCallUrlscan = forceUrlscan || redirectChain.size() > 1 || suspiciousDomain;
            if (shouldCallUrlscan) {
                UrlscanBehavior urlscanBehavior = scanWithUrlScan(finalUrl);
                if (urlscanBehavior.externalError) {
                    spamScore += 5;
                    reasons.add("Partial analysis due to urlscan failure");
                    checksPerformed.add("Behavior analysis unavailable");
                } else {
                    behavior = urlscanBehavior;
                    behavior.redirectChain = mergeRedirectChains(redirectChain, urlscanBehavior.redirectChain);
                    finalUrl = normalizeUrl(behavior.finalUrl);
                    finalDomain = extractDomain(finalUrl);
                    trustedDomain = isTrustedDomain(finalDomain);

                    if (behavior.redirectChain.size() > 1) {
                        redirectScore += 20;
                        reasons.add("URL redirects detected");
                    }
                    if (hasDomainChangeAcrossRedirects(behavior.redirectChain)) {
                        redirectScore += 20;
                        reasons.add("Domain changed across redirects");
                    }

                    if (behavior.scriptCount > 20) {
                        spamScore += 30;
                        reasons.add("High script activity detected");
                    }
                    if (behavior.contactedDomains.size() > 15) {
                        spamScore += 20;
                        reasons.add("High number of contacted domains");
                    }
                    checksPerformed.add("Behavior analysis completed");
                }
            } else {
                checksPerformed.add("Behavior analysis skipped");
            }

            if (isPiracyDomain(finalDomain)) {
                piracyScore += 70;
                reasons.add("Piracy domain detected");
            }

            if (isSuspiciousTld(getTld(finalDomain))) {
                domainScore += 20;
                reasons.add("Suspicious TLD detected");
            }
            if (hasSuspiciousDigitSubstitution(getPrimaryDomain(finalDomain))) {
                domainScore += 30;
                reasons.add("Possible typo domain pattern detected");
            }

            if (finalUrl.length() > maxUrlLength) {
                spamScore += 20;
                reasons.add("Long URL structure detected");
            }
            if (countQueryParams(finalUrl) >= 4) {
                spamScore += 20;
                reasons.add("Many query parameters detected");
            }
            if (!getMatchedKeywords(finalUrl, adKeywordsCsv).isEmpty()) {
                spamScore += 20;
                reasons.add("Ad/tracker URL patterns detected");
            }

            boolean safeBrowsingClean = !gsb.hasAnyThreat();
            boolean behaviorSignal = redirectScore > 0 || spamScore > 20;
            boolean shouldCallVirusTotal = safeBrowsingClean && behaviorSignal && !trustedDomain;
            if (shouldCallVirusTotal) {
                VirusTotalStats vt = checkVirusTotal(finalUrl);
                checksPerformed.add(vt.externalError
                        ? "VirusTotal reputation unavailable"
                        : "VirusTotal reputation checked");
                if (vt.malicious > 0) {
                    malwareScore += 80;
                }
                if (vt.suspicious > 0) {
                    malwareScore += 40;
                }
                int totalThreats = Math.max(0, vt.malicious) + Math.max(0, vt.suspicious);
                if (totalThreats > 0) {
                    reasons.add("VirusTotal flagged " + totalThreats + " engines");
                }
            } else {
                checksPerformed.add("VirusTotal reputation skipped");
            }

            if (trustedDomain) {
                spamScore = Math.max(0, spamScore - 10);
                redirectScore = Math.max(0, redirectScore - 10);
            }

            Map<String, Integer> breakdown = new LinkedHashMap<>();
            breakdown.put("malware", malwareScore);
            breakdown.put("phishing", phishingScore);
            breakdown.put("piracy", piracyScore);
            breakdown.put("spam", spamScore);
            breakdown.put("redirectRisk", redirectScore);
            breakdown.put("domainRisk", domainScore);

            Map<String, String> categoryLabels = new LinkedHashMap<>();
            categoryLabels.put("malware", toCategoryLabel(malwareScore));
            categoryLabels.put("phishing", toCategoryLabel(phishingScore));
            categoryLabels.put("piracy", toCategoryLabel(piracyScore));
            categoryLabels.put("spam", toCategoryLabel(spamScore));
            categoryLabels.put("redirectRisk", toCategoryLabel(redirectScore));
            categoryLabels.put("domainRisk", toCategoryLabel(domainScore));

            // STEP 8: finalScore is UI-only.
            int finalScore = calculateFinalScore(malwareScore, phishingScore, piracyScore, spamScore, redirectScore, domainScore);

            // STEP 1/10: status is category-threshold based, not finalScore.
            String status = determineStatus(malwareScore, phishingScore, piracyScore, spamScore, trustedDomain, gsb);
            String message = determineMessage(status, malwareScore, phishingScore, piracyScore, spamScore);

            if ("SAFE".equals(status) && isPiracyDomain(finalDomain)) {
                status = "SUSPICIOUS";
            }

            List<String> finalReasons = dedupeReasons(reasons);
            if (finalReasons.isEmpty()) {
                finalReasons.add("No malware detected by Google Safe Browsing");
            }

            UrlScanResponse response = new UrlScanResponse(
                    status,
                    message,
                    normalizedUrl,
                    finalScore,
                    breakdown,
                    categoryLabels,
                    dedupeReasons(checksPerformed),
                    finalReasons,
                    behavior.redirectChain,
                    finalUrl,
                    behavior.contactedDomains,
                    behavior.scriptCount,
                    behavior.pageTitle,
                    behavior.screenshotUrl
            );
            urlScanCacheService.put(finalUrl, response);
            if (!finalUrl.equalsIgnoreCase(resolvedFinalUrl)) {
                urlScanCacheService.put(resolvedFinalUrl, response);
            }
            return response;
        } catch (Exception ex) {
            log.error("Scan failed for URL: {}", normalizedUrl, ex);
            return buildErrorResponse(
                    "SCAN_ERROR",
                    "Scan failed due to internal or external provider issue.",
                    normalizedUrl,
                    List.of("Unable to complete scan right now. Please try again.")
            );
        }
    }

    private UrlScanResponse buildErrorResponse(String status, String message, String scannedUrl, List<String> reasons) {
        return new UrlScanResponse(
                status,
                message,
                scannedUrl,
                0,
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                new ArrayList<>(),
                reasons,
                new ArrayList<>(),
                scannedUrl,
                new ArrayList<>(),
                0,
                "",
                ""
        );
    }

    // Google Safe Browsing API integration (primary signal)
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

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.postForEntity(requestUri, requestEntity, String.class);
            return parseSafeBrowsingResult(response.getBody());
        } catch (Exception ex) {
            log.error("Google Safe Browsing failed for URL: {}", url, ex);
            return GoogleSafeBrowsingResult.unavailable();
        }
    }

    private GoogleSafeBrowsingResult parseSafeBrowsingResult(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return new GoogleSafeBrowsingResult(false, false, false);
        }

        JsonNode root = objectMapper.readTree(body);
        JsonNode matches = root.path("matches");
        boolean malware = false;
        boolean phishing = false;

        if (matches.isArray()) {
            for (JsonNode match : matches) {
                String threatType = match.path("threatType").asText("");
                if ("MALWARE".equalsIgnoreCase(threatType)) {
                    malware = true;
                }
                if ("SOCIAL_ENGINEERING".equalsIgnoreCase(threatType)) {
                    phishing = true;
                }
            }
        }

        return new GoogleSafeBrowsingResult(malware, phishing, false);
    }

    // Controlled VirusTotal integration (secondary signal)
    private VirusTotalStats checkVirusTotal(String url) {
        if (virusTotalApiKey == null || virusTotalApiKey.isBlank()) {
            return VirusTotalStats.unavailable();
        }

        try {
            URI lookupUri = UriComponentsBuilder
                    .fromUriString(virusTotalUrl)
                    .pathSegment(encodeUrlForVirusTotal(url))
                    .build(true)
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.add("x-apikey", virusTotalApiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(lookupUri, HttpMethod.GET, entity, String.class);
            String body = response.getBody();

            if (body == null || body.isBlank()) {
                return VirusTotalStats.unavailable();
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode stats = root.path("data").path("attributes").path("last_analysis_stats");
            return VirusTotalStats.of(stats.path("malicious").asInt(0), stats.path("suspicious").asInt(0));
        } catch (HttpClientErrorException.NotFound ex) {
            return VirusTotalStats.of(0, 0);
        } catch (Exception ex) {
            log.error("VirusTotal failed for URL: {}", url, ex);
            return VirusTotalStats.unavailable();
        }
    }

    // urlscan behavior integration
    private UrlscanBehavior scanWithUrlScan(String url) {
        if (urlscanApiKey == null || urlscanApiKey.isBlank()) {
            return UrlscanBehavior.externalFailure(url);
        }

        try {
            String uuid = submitUrlscanJob(url);
            if (uuid == null || uuid.isBlank()) {
                return UrlscanBehavior.externalFailure(url);
            }
            return pollUrlscanResult(uuid, url);
        } catch (Exception ex) {
            log.error("urlscan submit failed for URL: {}", url, ex);
            return UrlscanBehavior.externalFailure(url);
        }
    }

    private String submitUrlscanJob(String url) throws Exception {
        RestTemplate restTemplate = new RestTemplate();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("url", url);
        payload.put("visibility", "public");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("API-Key", urlscanApiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(urlscanSubmitUrl, request, String.class);
        String body = response.getBody();

        if (body == null || body.isBlank()) {
            return null;
        }

        JsonNode root = objectMapper.readTree(body);
        return root.path("uuid").asText(null);
    }

    private UrlscanBehavior pollUrlscanResult(String uuid, String originalUrl) {
        RestTemplate restTemplate = new RestTemplate();

        for (int attempt = 1; attempt <= urlscanPollMaxAttempts; attempt++) {
            try {
                String resultUrl = String.format(urlscanResultUrlTemplate, uuid);

                HttpHeaders headers = new HttpHeaders();
                headers.add("API-Key", urlscanApiKey);
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(resultUrl, HttpMethod.GET, entity, String.class);
                String body = response.getBody();
                if (body != null && !body.isBlank()) {
                    JsonNode root = objectMapper.readTree(body);
                    return parseUrlscanResult(root, originalUrl);
                }
            } catch (HttpClientErrorException.NotFound ex) {
                // Result not ready yet.
            } catch (Exception ex) {
                log.error("urlscan poll failed for UUID: {}", uuid, ex);
                return UrlscanBehavior.externalFailure(originalUrl);
            }

            try {
                Thread.sleep(urlscanPollDelayMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return UrlscanBehavior.externalFailure(originalUrl);
            }
        }

        return UrlscanBehavior.externalFailure(originalUrl);
    }

    private UrlscanBehavior parseUrlscanResult(JsonNode root, String originalUrl) {
        UrlscanBehavior behavior = UrlscanBehavior.empty(originalUrl);

        String finalUrl = root.path("page").path("url").asText("");
        if (finalUrl.isBlank()) {
            finalUrl = root.path("task").path("url").asText(originalUrl);
        }
        behavior.finalUrl = normalizeUrl(finalUrl);

        JsonNode redirectsNode = root.path("lists").path("redirects");
        behavior.redirectChain = new ArrayList<>();
        if (redirectsNode.isArray()) {
            for (JsonNode node : redirectsNode) {
                String redirect = node.asText("").trim();
                if (!redirect.isBlank()) {
                    behavior.redirectChain.add(normalizeUrl(redirect));
                }
            }
        }
        if (behavior.redirectChain.isEmpty()) {
            behavior.redirectChain.add(normalizeUrl(originalUrl));
            if (!behavior.finalUrl.equalsIgnoreCase(normalizeUrl(originalUrl))) {
                behavior.redirectChain.add(behavior.finalUrl);
            }
        }

        JsonNode domainsNode = root.path("lists").path("domains");
        behavior.contactedDomains = new ArrayList<>();
        if (domainsNode.isArray()) {
            for (JsonNode node : domainsNode) {
                String domain = node.asText("").trim().toLowerCase();
                if (!domain.isBlank()) {
                    behavior.contactedDomains.add(domain);
                }
            }
        }

        JsonNode scriptsNode = root.path("lists").path("scripts");
        behavior.scriptCount = scriptsNode.isArray() ? scriptsNode.size() : 0;
        behavior.pageTitle = root.path("page").path("title").asText("");
        behavior.screenshotUrl = root.path("task").path("screenshotURL").asText("");
        if (behavior.screenshotUrl.isBlank()) {
            behavior.screenshotUrl = root.path("page").path("screenshot").asText("");
        }

        return behavior;
    }

    private Map<String, Object> buildSafeBrowsingPayload(String url) {
        Map<String, Object> client = new LinkedHashMap<>();
        client.put("clientId", "url-shortener");
        client.put("clientVersion", "1.0");

        Map<String, Object> threatEntry = new LinkedHashMap<>();
        threatEntry.put("url", url);

        Map<String, Object> threatInfo = new LinkedHashMap<>();
        threatInfo.put("threatTypes", List.of("MALWARE", "SOCIAL_ENGINEERING"));
        threatInfo.put("platformTypes", List.of("ANY_PLATFORM"));
        threatInfo.put("threatEntryTypes", List.of("URL"));
        threatInfo.put("threatEntries", List.of(threatEntry));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("client", client);
        payload.put("threatInfo", threatInfo);
        return payload;
    }

    private String normalizeUrl(String input) {
        if (input == null) {
            return "";
        }

        String normalized = input.trim();
        if (!normalized.isBlank()
                && !normalized.toLowerCase().startsWith("http://")
                && !normalized.toLowerCase().startsWith("https://")) {
            normalized = "https://" + normalized;
        }

        if (normalized.endsWith("/") && normalized.length() > "https://".length()) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    private boolean isTrustedDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return false;
        }

        for (String trusted : trustedDomainsCsv.split(",")) {
            String clean = trusted.trim().toLowerCase();
            if (!clean.isBlank() && (domain.equals(clean) || domain.endsWith("." + clean))) {
                return true;
            }
        }
        return false;
    }

    private boolean isKnownShortener(String domain) {
        if (domain == null || domain.isBlank()) {
            return false;
        }
        return Set.of("bit.ly", "t.co", "tinyurl.com", "goo.gl", "ow.ly", "is.gd").contains(domain);
    }

    private boolean isSuspiciousDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return false;
        }
        return isSuspiciousTld(getTld(domain)) || hasSuspiciousDigitSubstitution(getPrimaryDomain(domain));
    }

    private boolean hasDomainChangeAcrossRedirects(List<String> redirectChain) {
        if (redirectChain == null || redirectChain.size() < 2) {
            return false;
        }

        Set<String> domains = new LinkedHashSet<>();
        for (String url : redirectChain) {
            String domain = extractDomain(url);
            if (!domain.isBlank()) {
                domains.add(domain);
            }
        }
        return domains.size() > 1;
    }

    private boolean containsAdTrackerKeyword(List<String> domains) {
        if (domains == null || domains.isEmpty()) {
            return false;
        }

        for (String domain : domains) {
            String lower = domain.toLowerCase();
            for (String keyword : List.of("ad", "ads", "track", "tracker", "click", "banner", "redirect")) {
                if (lower.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsPiracyTitle(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }

        String lower = title.toLowerCase();
        for (String keyword : List.of("download", "watch", "free", "movie")) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPiracyDomain(String domain) {
        String value = domain == null ? "" : domain.toLowerCase();
        return value.contains("movierulz") || value.contains("torrent") || value.contains("123movies");
    }

    private List<String> getMatchedKeywords(String source, String csvKeywords) {
        List<String> matches = new ArrayList<>();
        String lowerSource = source == null ? "" : source.toLowerCase();

        for (String raw : csvKeywords.split(",")) {
            String keyword = raw.trim().toLowerCase();
            if (!keyword.isBlank() && lowerSource.contains(keyword)) {
                matches.add(keyword);
            }
        }
        return matches;
    }

    private int countQueryParams(String url) {
        int questionIndex = url.indexOf('?');
        if (questionIndex < 0 || questionIndex >= url.length() - 1) {
            return 0;
        }

        int count = 1;
        for (char ch : url.substring(questionIndex + 1).toCharArray()) {
            if (ch == '&') {
                count++;
            }
        }
        return count;
    }

    private String extractDomain(String url) {
        try {
            URL parsed = new URL(url);
            return parsed.getHost() == null ? "" : parsed.getHost().toLowerCase();
        } catch (Exception ex) {
            return "";
        }
    }

    private String getPrimaryDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return "";
        }
        String[] parts = domain.split("\\.");
        return parts.length >= 2 ? parts[parts.length - 2] : domain;
    }

    private String getTld(String domain) {
        if (domain == null || domain.isBlank() || !domain.contains(".")) {
            return "";
        }
        String[] parts = domain.split("\\.");
        return parts[parts.length - 1].toLowerCase();
    }

    private boolean isSuspiciousTld(String tld) {
        if (tld == null || tld.isBlank()) {
            return false;
        }

        for (String raw : suspiciousTldsCsv.split(",")) {
            if (tld.equals(raw.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSuspiciousDigitSubstitution(String primaryDomain) {
        if (primaryDomain == null || primaryDomain.isBlank()) {
            return false;
        }

        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char ch : primaryDomain.toCharArray()) {
            if (Character.isLetter(ch)) {
                hasLetter = true;
            }
            if (Character.isDigit(ch)) {
                hasDigit = true;
            }
        }
        return hasLetter && hasDigit;
    }

    private int calculateFinalScore(int malware, int phishing, int piracy, int spam, int redirectRisk, int domainRisk) {
        double weighted = malware
                + phishing
                + (piracy * 0.6)
                + (spam * 0.4)
                + redirectRisk
                + domainRisk;

        return Math.max(0, Math.min(100, (int) Math.round(weighted)));
    }

    private String determineStatus(int malware, int phishing, int piracy, int spam, boolean trustedDomain, GoogleSafeBrowsingResult gsb) {
        if (malware >= 70) {
            return "DANGEROUS";
        }
        if (piracy >= 60) {
            return "SUSPICIOUS";
        }
        if (phishing >= 70 || spam >= 60) {
            return "SUSPICIOUS";
        }

        if (trustedDomain && !gsb.hasAnyThreat()) {
            return "SAFE";
        }

        return "SAFE";
    }

    private String determineMessage(String status, int malware, int phishing, int piracy, int spam) {
        if ("DANGEROUS".equals(status) && malware >= 70) {
            return "Malware risk confirmed by high-confidence signals";
        }
        if ("SUSPICIOUS".equals(status) && piracy >= 60) {
            return "Piracy-related behavior identified";
        }
        if ("SUSPICIOUS".equals(status) && spam >= 60) {
            return "Spam/ad-heavy behavior identified";
        }
        if (phishing >= 70) {
            return "Phishing-like patterns detected";
        }
        return "No malware detected";
    }

    private String toCategoryLabel(int score) {
        if (score >= 70) {
            return "HIGH";
        }
        if (score >= 30) {
            return "MEDIUM";
        }
        return "SAFE";
    }

    private String encodeUrlForVirusTotal(String url) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(url.getBytes(StandardCharsets.UTF_8));
    }

    private List<String> dedupeReasons(List<String> reasons) {
        return new ArrayList<>(new LinkedHashSet<>(reasons));
    }

    private List<String> mergeRedirectChains(List<String> resolverChain, List<String> behaviorChain) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (resolverChain != null) {
            merged.addAll(resolverChain);
        }
        if (behaviorChain != null) {
            merged.addAll(behaviorChain);
        }
        return new ArrayList<>(merged);
    }

    public String submitAsyncScan(String url) {
        cleanupExpiredAsyncJobs();
        long pendingJobs = asyncJobs.values().stream().filter(AsyncScanJob::isPending).count();
        if (pendingJobs >= maxAsyncJobs) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many async scan jobs");
        }

        String normalized = normalizeUrl(url);
        String jobId = UUID.randomUUID().toString();
        AsyncScanJob job = new AsyncScanJob(jobId);
        asyncJobs.put(jobId, job);

        CompletableFuture
                .supplyAsync(() -> scanUrlInternal(normalized, true), asyncExecutor)
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        completeWithFallback(jobId, normalized);
                    } else {
                        completeJob(jobId, response);
                    }
                });

        asyncScheduler.schedule(() -> completeWithFallback(jobId, normalized), asyncTimeoutMs, TimeUnit.MILLISECONDS);
        return jobId;
    }

    public UrlScanAsyncStatusResponse getAsyncScanStatus(String jobId) {
        cleanupExpiredAsyncJobs();
        AsyncScanJob job = asyncJobs.get(jobId);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Async scan job not found");
        }
        return new UrlScanAsyncStatusResponse(job.jobId, job.status, job.result, job.errorMessage);
    }

    private void completeWithFallback(String jobId, String url) {
        AsyncScanJob job = asyncJobs.get(jobId);
        if (job == null || !job.isPending()) {
            return;
        }

        UrlScanResponse fallback = scanUrlInternal(url, false);
        List<String> reasons = fallback.getReasons() == null ? new ArrayList<>() : new ArrayList<>(fallback.getReasons());
        reasons.add("Fallback mode activated due to async failure");
        fallback.setReasons(dedupeReasons(reasons));
        completeJob(jobId, fallback);
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

    public void runScheduledAsyncCleanup() {
        cleanupExpiredAsyncJobs();
    }

    private void cleanupExpiredAsyncJobs() {
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

    public List<UrlScanHistoryResponse> getScanHistory(int limit, String status) {
        return List.of();
    }

    public UrlScanHistoryResponse getScanHistoryById(Long id) {
        return new UrlScanHistoryResponse(
                id,
                null,
                "NOT_FOUND",
                "Scan history is currently disabled",
                0,
                List.of(),
                null
        );
    }

    public void clearScanCache() {
        log.info("Scan cache clear requested, but cache is currently disabled");
    }


    private static class GoogleSafeBrowsingResult {
        private final boolean malwareThreat;
        private final boolean phishingThreat;
        private final boolean externalError;

        private GoogleSafeBrowsingResult(boolean malwareThreat, boolean phishingThreat, boolean externalError) {
            this.malwareThreat = malwareThreat;
            this.phishingThreat = phishingThreat;
            this.externalError = externalError;
        }

        private static GoogleSafeBrowsingResult unavailable() {
            return new GoogleSafeBrowsingResult(false, false, true);
        }

        private boolean hasAnyThreat() {
            return malwareThreat || phishingThreat;
        }
    }

    private static class VirusTotalStats {
        private final int malicious;
        private final int suspicious;
        private final boolean externalError;

        private VirusTotalStats(int malicious, int suspicious, boolean externalError) {
            this.malicious = malicious;
            this.suspicious = suspicious;
            this.externalError = externalError;
        }

        private static VirusTotalStats of(int malicious, int suspicious) {
            return new VirusTotalStats(malicious, suspicious, false);
        }

        private static VirusTotalStats unavailable() {
            return new VirusTotalStats(0, 0, true);
        }
    }

    private static class UrlscanBehavior {
        private List<String> redirectChain;
        private String finalUrl;
        private List<String> contactedDomains;
        private int scriptCount;
        private String pageTitle;
        private String screenshotUrl;
        private boolean externalError;

        private static UrlscanBehavior empty(String baseUrl) {
            UrlscanBehavior behavior = new UrlscanBehavior();
            behavior.redirectChain = new ArrayList<>();
            behavior.redirectChain.add(baseUrl);
            behavior.finalUrl = baseUrl;
            behavior.contactedDomains = new ArrayList<>();
            behavior.scriptCount = 0;
            behavior.pageTitle = "";
            behavior.screenshotUrl = "";
            behavior.externalError = false;
            return behavior;
        }

        private static UrlscanBehavior externalFailure(String baseUrl) {
            UrlscanBehavior behavior = empty(baseUrl);
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
