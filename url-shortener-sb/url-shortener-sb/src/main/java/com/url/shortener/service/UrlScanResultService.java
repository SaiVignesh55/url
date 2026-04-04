package com.url.shortener.service;

import com.url.shortener.dtos.UrlScanResponse;
import com.url.shortener.dtos.GeoData;
import com.url.shortener.models.UrlScanResult;
import com.url.shortener.repository.UrlScanResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlScanResultService {

    private final UrlScanResultRepository urlScanResultRepository;
    private final GeoApiService geoApiService;

    @Transactional(readOnly = true)
    public Optional<UrlScanResponse> findByScannedUrl(String scannedUrl) {
        if (scannedUrl == null || scannedUrl.isBlank()) {
            return Optional.empty();
        }

        log.debug("Fetching scan result from DB for scannedUrl={}", scannedUrl);
        try {
            Optional<UrlScanResult> existing = urlScanResultRepository.findTopByScannedUrlOrderByCreatedAtDesc(scannedUrl);
            if (existing.isEmpty()) {
                return Optional.empty();
            }

            UrlScanResult row = existing.get();
            if (!isCacheableStatus(row.getStatus())) {
                return Optional.empty();
            }

            return Optional.of(toResponse(row));
        } catch (Exception ex) {
            log.error("Failed to fetch scan result from database for scannedUrl={}", scannedUrl, ex);
            return Optional.empty();
        }
    }

    @Transactional
    public UrlScanResult saveScanResult(UrlScanResponse response) {
        UrlScanResult scanResult = new UrlScanResult();
        String scannedUrl = normalizeUrl(response.getScannedUrl(), response.getFinalUrl());
        String verdict = normalizeVerdict(response.getVerdict(), response.getStatus());
        String status = response.getStatus() == null || response.getStatus().isBlank() ? verdict : response.getStatus();

        scanResult.setUrl(normalizeUrl(response.getFinalUrl(), scannedUrl));
        scanResult.setScannedUrl(scannedUrl);
        scanResult.setStatus(status);
        scanResult.setMessage(response.getMessage());
        scanResult.setReasons(serializeReasons(response.getReasons()));
        scanResult.setRiskScore(response.getFinalScore());
        scanResult.setFinalVerdict(verdict);
        scanResult.setScore(response.getFinalScore());
        scanResult.setMalwareScore(response.getMalwareScore());
        scanResult.setPhishingScore(response.getPhishingScore());
        scanResult.setSpamScore(response.getSpamScore());
        scanResult.setRedirectRisk(response.getRedirectScore());
        scanResult.setDomainRisk(response.getDomainScore());
        String urlscanScanId = emptyIfNull(response.getUrlscanScanId());
        scanResult.setUrlscanScanId(urlscanScanId);
        scanResult.setScreenshotUrl(normalizeUrl(response.getScreenshotUrl(), buildUrlscanScreenshotUrl(urlscanScanId)));
        scanResult.setRedirectChain(serializeRedirectChain(response.getRedirectChain()));
        scanResult.setFinalUrl(normalizeUrl(response.getFinalUrl(), scannedUrl));
        scanResult.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));

        String targetUrl = normalizeUrl(scanResult.getFinalUrl(), scanResult.getScannedUrl());
        GeoData geo = geoApiService.getRegionFromUrl(targetUrl);
        scanResult.setCountry(trimValue(geo.getCountry(), 100));
        scanResult.setRegion(trimValue(geo.getRegion(), 100));
        scanResult.setCity(trimValue(geo.getCity(), 100));

        try {
            log.info("Saving scan result for scannedUrl={} status={}", scannedUrl, status);
            UrlScanResult saved = urlScanResultRepository.save(scanResult);
            log.info("SAVED TO DB");
            log.info("Saved scan result with id={}", saved.getId());
            return saved;
        } catch (Exception ex) {
            log.error("DB save failed for scannedUrl={} error={}", scannedUrl, ex.getMessage());
            log.error("Failed to save url scan result for url={} verdict={}", scanResult.getUrl(), scanResult.getFinalVerdict(), ex);
            throw ex;
        }
    }

    private UrlScanResponse toResponse(UrlScanResult row) {
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        breakdown.put("malware", safeInt(row.getMalwareScore()));
        breakdown.put("phishing", safeInt(row.getPhishingScore()));
        breakdown.put("spam", safeInt(row.getSpamScore()));
        breakdown.put("piracy", 0);
        breakdown.put("redirect", safeInt(row.getRedirectRisk()));
        breakdown.put("domain", safeInt(row.getDomainRisk()));

        UrlScanResponse response = new UrlScanResponse(
                normalizeVerdict(row.getFinalVerdict(), row.getStatus()),
                row.getMessage() == null ? "Result returned from DB cache" : row.getMessage(),
                row.getScannedUrl(),
                safeInt(row.getScore()),
                breakdown,
                buildCategoryLabels(breakdown),
                new ArrayList<>(List.of("Database cache")),
                parseReasons(row.getReasons()),
                parseRedirectChain(row.getRedirectChain(), row.getScannedUrl()),
                normalizeUrl(row.getFinalUrl(), row.getUrl()),
                new ArrayList<>(),
                0,
                "",
                emptyIfNull(row.getScreenshotUrl())
        );

        response.setVerdict(normalizeVerdict(row.getFinalVerdict(), row.getStatus()));
        response.setStatus(row.getStatus());
        response.setMalwareScore(safeInt(row.getMalwareScore()));
        response.setPhishingScore(safeInt(row.getPhishingScore()));
        response.setSpamScore(safeInt(row.getSpamScore()));
        response.setRedirectScore(safeInt(row.getRedirectRisk()));
        response.setDomainScore(safeInt(row.getDomainRisk()));
        response.setFinalScore(safeInt(row.getScore()));
        String urlscanScanId = emptyIfNull(row.getUrlscanScanId());
        response.setUrlscanScanId(urlscanScanId);
        response.setScreenshotUrl(normalizeUrl(row.getScreenshotUrl(), buildUrlscanScreenshotUrl(urlscanScanId)));
        response.setResultUrl(buildUrlscanResultUrl(urlscanScanId));
        return response;
    }

    private String buildUrlscanScreenshotUrl(String urlscanScanId) {
        if (urlscanScanId == null || urlscanScanId.isBlank()) {
            return "";
        }
        return "https://urlscan.io/screenshots/" + urlscanScanId + ".png";
    }

    private String buildUrlscanResultUrl(String urlscanScanId) {
        if (urlscanScanId == null || urlscanScanId.isBlank()) {
            return "";
        }
        return "https://urlscan.io/result/" + urlscanScanId + "/";
    }

    private List<String> parseRedirectChain(String redirectChain, String fallbackUrl) {
        String safeFallback = normalizeUrl(fallbackUrl, "unknown");
        if (redirectChain == null || redirectChain.isBlank()) {
            return new ArrayList<>(List.of(safeFallback));
        }
        List<String> values = new ArrayList<>();
        for (String token : redirectChain.split("\\n")) {
            String cleaned = token.trim();
            if (!cleaned.isBlank()) {
                values.add(cleaned);
            }
        }
        return values.isEmpty() ? new ArrayList<>(List.of(safeFallback)) : values;
    }

    private String serializeRedirectChain(List<String> redirectChain) {
        if (redirectChain == null || redirectChain.isEmpty()) {
            return null;
        }
        List<String> clean = redirectChain.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        return clean.isEmpty() ? null : String.join("\n", clean);
    }

    private Map<String, String> buildCategoryLabels(Map<String, Integer> breakdown) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : breakdown.entrySet()) {
            labels.put(entry.getKey(), level(entry.getValue()));
        }
        return labels;
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

    private List<String> parseReasons(String reasons) {
        if (reasons == null || reasons.isBlank()) {
            return new ArrayList<>();
        }
        List<String> values = new ArrayList<>();
        for (String token : reasons.split("\\|")) {
            String cleaned = token.trim();
            if (!cleaned.isBlank()) {
                values.add(cleaned);
            }
        }
        return values;
    }

    private String serializeReasons(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return null;
        }
        List<String> clean = reasons.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        return clean.isEmpty() ? null : String.join(" | ", clean);
    }

    private boolean isCacheableStatus(String status) {
        if (status == null) {
            return false;
        }
        String value = status.toUpperCase();
        return "SAFE".equals(value) || "SUSPICIOUS".equals(value) || "MALICIOUS".equals(value);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalizeUrl(String scannedUrl, String finalUrl) {
        if (scannedUrl != null && !scannedUrl.isBlank()) {
            return scannedUrl;
        }
        if (finalUrl != null && !finalUrl.isBlank()) {
            return finalUrl;
        }
        return "unknown";
    }

    private String normalizeVerdict(String verdict, String status) {
        if (verdict != null && !verdict.isBlank()) {
            return verdict;
        }
        if (status != null && !status.isBlank()) {
            return status;
        }
        return "UNKNOWN";
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private String trimValue(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}

