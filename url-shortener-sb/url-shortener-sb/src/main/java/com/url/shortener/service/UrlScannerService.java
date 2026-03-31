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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UrlScannerService {

    private static final Logger log = LoggerFactory.getLogger(UrlScannerService.class);

    @Value("${url.scan.max-length:120}")
    private int maxUrlLength;

    @Value("${url.scan.score.no-https:20}")
    private int noHttpsScore;

    @Value("${url.scan.score.long-url:20}")
    private int longUrlScore;

    @Value("${url.scan.score.suspicious-keywords:20}")
    private int suspiciousKeywordsScore;

    @Value("${url.scan.score.google-threat:60}")
    private int googleThreatScore;

    @Value("${url.scan.score.virustotal-threat:40}")
    private int virusTotalThreatScore;

    @Value("${url.scan.score.virustotal-suspicious:10}")
    private int virusTotalSuspiciousScore;

    @Value("${url.scan.threshold.suspicious:30}")
    private int suspiciousThreshold;

    @Value("${url.scan.threshold.unsafe:60}")
    private int unsafeThreshold;

    @Value("${url.scan.suspicious-keywords:login,verify,bank,password,free,gift,wallet,update}")
    private String suspiciousKeywordsCsv;

    @Value("${google.safe-browsing.api-key:}")
    private String safeBrowsingApiKey;

    @Value("${google.safe-browsing.url}")
    private String safeBrowsingUrl;

    @Value("${virustotal.url:https://www.virustotal.com/api/v3/urls}")
    private String virusTotalUrl;

    @Value("${virustotal.api-key:}")
    private String virusTotalApiKey;

    private final ObjectMapper objectMapper;

    public UrlScanResponse scanUrl(String url) {
        String normalizedUrl = normalizeUrl(url);
        if (normalizedUrl.isBlank()) {
            return new UrlScanResponse(
                    "INVALID_REQUEST",
                    "URL is required",
                    null,
                    0,
                    List.of("Please provide a valid URL in request body")
            );
        }

        try {
            int riskScore = 0;
            List<String> reasons = new ArrayList<>();

            if (!normalizedUrl.toLowerCase().startsWith("https://")) {
                riskScore += noHttpsScore;
                reasons.add("URL is not using HTTPS");
            }

            if (normalizedUrl.length() > maxUrlLength) {
                riskScore += longUrlScore;
                reasons.add("URL length is unusually long");
            }

            List<String> foundKeywords = getSuspiciousKeywords(normalizedUrl);
            if (!foundKeywords.isEmpty()) {
                riskScore += suspiciousKeywordsScore;
                reasons.add("Suspicious keywords found: " + String.join(", ", foundKeywords));
            }

            ProviderResult google = checkGoogleSafeBrowsing(normalizedUrl);
            ProviderResult virusTotal = checkVirusTotal(normalizedUrl);

            if (google.threatDetected) {
                riskScore += googleThreatScore;
                reasons.add("Google Safe Browsing reported this URL as unsafe");
            } else if (google.reason != null) {
                reasons.add(google.reason);
            }

            if (virusTotal.scoreContribution > 0) {
                riskScore += virusTotal.scoreContribution;
            }
            if (virusTotal.reason != null) {
                reasons.add(virusTotal.reason);
            }

            riskScore = Math.min(riskScore, 100);
            String status = getStatusByRiskScore(riskScore);
            String message = "Scan completed";

            return new UrlScanResponse(status, message, normalizedUrl, riskScore, reasons);
        } catch (Exception ex) {
            log.error("Scan failed for URL: {}", normalizedUrl, ex);
            return new UrlScanResponse(
                    "SCAN_ERROR",
                    "Scan failed due to internal or external provider issue.",
                    normalizedUrl,
                    0,
                    List.of("Unable to complete scan right now. Please try again.")
            );
        }
    }

    private ProviderResult checkGoogleSafeBrowsing(String url) {
        if (safeBrowsingApiKey == null || safeBrowsingApiKey.isBlank()) {
            return ProviderResult.info("Google Safe Browsing API key is missing, external check skipped");
        }

        try {
            URI requestUri = UriComponentsBuilder
                    .fromHttpUrl(safeBrowsingUrl)
                    .queryParam("key", safeBrowsingApiKey)
                    .build(true)
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(buildSafeBrowsingPayload(url), headers);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.postForEntity(requestUri, requestEntity, String.class);
            String responseBody = response == null ? null : response.getBody();

            if (hasThreatMatches(responseBody)) {
                return ProviderResult.threat();
            }
            return ProviderResult.info("Google Safe Browsing found no threat match");
        } catch (HttpClientErrorException.TooManyRequests ex) {
            log.error("Google Safe Browsing rate limit (429) for URL: {}", url, ex);
            return ProviderResult.info("Google Safe Browsing unavailable (429 rate limit)");
        } catch (Exception ex) {
            log.error("Google Safe Browsing failed for URL: {}", url, ex);
            return ProviderResult.info("Google Safe Browsing check failed (network/API issue)");
        }
    }

    private ProviderResult checkVirusTotal(String url) {
        if (virusTotalApiKey == null || virusTotalApiKey.isBlank()) {
            return ProviderResult.info("VirusTotal API key is missing, external check skipped");
        }

        try {
            URI lookupUri = UriComponentsBuilder
                    .fromHttpUrl(virusTotalUrl)
                    .pathSegment(encodeUrlForVirusTotal(url))
                    .build(true)
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.add("x-apikey", virusTotalApiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(
                    lookupUri,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            String responseBody = (response != null) ? response.getBody() : null;

            if (responseBody == null || responseBody.isBlank()) {
                return ProviderResult.info("VirusTotal unavailable (empty response)");
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode stats = root.path("data").path("attributes").path("last_analysis_stats");

            int malicious = stats.path("malicious").asInt(0);
            int suspicious = stats.path("suspicious").asInt(0);

            // =========================
            // ✅ FIXED DECISION LOGIC
            // =========================

            // Strong malicious consensus → Dangerous
            if (malicious >= 5) {
                return ProviderResult.scored(
                        virusTotalThreatScore,
                        "VirusTotal confirmed malicious by multiple engines: " + malicious
                );
            }

            // Moderate signals → Suspicious
            if (malicious >= 2) {
                return ProviderResult.scored(
                        20,
                        "VirusTotal shows multiple detections: " + malicious
                );
            }

            // Single detection → Ignore (false positive)
            if (malicious == 1) {
                return ProviderResult.info(
                        "Single engine flagged (likely false positive)"
                );
            }

            // Weak suspicious signals
            if (suspicious > 2) {
                return ProviderResult.scored(
                        virusTotalSuspiciousScore,
                        "Multiple suspicious signals: " + suspicious
                );
            }

            return ProviderResult.info("VirusTotal found no significant threats");

        } catch (HttpClientErrorException.NotFound ex) {
            return ProviderResult.info("VirusTotal has no existing report for this URL yet");
        } catch (HttpClientErrorException.TooManyRequests ex) {
            log.error("VirusTotal rate limit (429) for URL: {}", url, ex);
            return ProviderResult.info("VirusTotal unavailable (429 rate limit)");
        } catch (Exception ex) {
            log.error("VirusTotal failed for URL: {}", url, ex);
            return ProviderResult.info("VirusTotal check failed (network/API issue)");
        }
    }

    private Map<String, Object> buildSafeBrowsingPayload(String url) {
        Map<String, Object> client = new HashMap<>();
        client.put("clientId", "url-shortener");
        client.put("clientVersion", "1.0");

        Map<String, Object> threatEntry = new HashMap<>();
        threatEntry.put("url", url);

        Map<String, Object> threatInfo = new HashMap<>();
        threatInfo.put("threatTypes", List.of("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"));
        threatInfo.put("platformTypes", List.of("ANY_PLATFORM"));
        threatInfo.put("threatEntryTypes", List.of("URL"));
        threatInfo.put("threatEntries", List.of(threatEntry));

        Map<String, Object> payload = new HashMap<>();
        payload.put("client", client);
        payload.put("threatInfo", threatInfo);
        return payload;
    }

    private boolean hasThreatMatches(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return false;
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode matches = root.get("matches");
        return matches != null && matches.isArray() && matches.size() > 0;
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

    private List<String> getSuspiciousKeywords(String url) {
        String lowerUrl = url.toLowerCase();
        List<String> found = new ArrayList<>();
        String[] keywords = suspiciousKeywordsCsv.split(",");

        for (String keywordRaw : keywords) {
            String keyword = keywordRaw.trim().toLowerCase();
            if (!keyword.isEmpty() && lowerUrl.contains(keyword)) {
                found.add(keyword);
            }
        }
        return found;
    }

    private String getStatusByRiskScore(int riskScore) {
        if (riskScore >= unsafeThreshold) {
            return "DANGEROUS";
        }
        if (riskScore >= suspiciousThreshold) {
            return "SUSPICIOUS";
        }
        return "SAFE";
    }

    private String encodeUrlForVirusTotal(String url) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(url.getBytes(StandardCharsets.UTF_8));
    }

    private static class ProviderResult {
        private final boolean threatDetected;
        private final int scoreContribution;
        private final String reason;

        private ProviderResult(boolean threatDetected, int scoreContribution, String reason) {
            this.threatDetected = threatDetected;
            this.scoreContribution = scoreContribution;
            this.reason = reason;
        }

        private static ProviderResult threat() {
            return new ProviderResult(true, 0, null);
        }

        private static ProviderResult scored(int scoreContribution, String reason) {
            return new ProviderResult(false, scoreContribution, reason);
        }

        private static ProviderResult info(String reason) {
            return new ProviderResult(false, 0, reason);
        }
    }

    // Compatibility methods kept so existing controller endpoints can compile.
    public String submitAsyncScan(String url) {
        return java.util.UUID.randomUUID().toString();
    }

    public UrlScanAsyncStatusResponse getAsyncScanStatus(String jobId) {
        return new UrlScanAsyncStatusResponse(
                jobId,
                "NOT_SUPPORTED",
                null,
                "Async scan is currently disabled"
        );
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
}
