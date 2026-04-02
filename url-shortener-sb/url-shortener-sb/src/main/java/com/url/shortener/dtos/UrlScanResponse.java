package com.url.shortener.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UrlScanResponse {
    private String status;
    private String verdict;
    private String message;
    private String scannedUrl;
    private int malwareScore;
    private int phishingScore;
    private int spamScore;
    private int piracyScore;
    private int redirectScore;
    private int domainScore;
    private int finalScore;
    private Map<String, Integer> breakdown;
    private Map<String, String> categoryLabels;
    private List<String> checksPerformed;
    private List<String> reasons;

    // urlscan behavior insights
    private List<String> redirectChain;
    private String finalUrl;
    private List<String> contactedDomains;
    private int scriptCount;
    private int totalRequests;
    private String pageTitle;
    private String screenshotUrl;

    // Compatibility constructor used by existing service/controller/tests.
    public UrlScanResponse(
            String status,
            String message,
            String scannedUrl,
            int finalScore,
            Map<String, Integer> breakdown,
            Map<String, String> categoryLabels,
            List<String> checksPerformed,
            List<String> reasons,
            List<String> redirectChain,
            String finalUrl,
            List<String> contactedDomains,
            int scriptCount,
            String pageTitle,
            String screenshotUrl
    ) {
        this.status = status;
        this.verdict = status;
        this.message = message;
        this.scannedUrl = scannedUrl;
        this.finalScore = finalScore;
        this.breakdown = breakdown == null ? new LinkedHashMap<>() : breakdown;
        this.categoryLabels = categoryLabels == null ? new LinkedHashMap<>() : categoryLabels;
        this.checksPerformed = checksPerformed == null ? new ArrayList<>() : checksPerformed;
        this.reasons = reasons == null ? new ArrayList<>() : reasons;
        this.redirectChain = redirectChain == null ? new ArrayList<>() : redirectChain;
        this.finalUrl = finalUrl;
        this.contactedDomains = contactedDomains == null ? new ArrayList<>() : contactedDomains;
        this.scriptCount = scriptCount;
        this.totalRequests = 0;
        this.pageTitle = pageTitle;
        this.screenshotUrl = screenshotUrl;

        this.malwareScore = this.breakdown.getOrDefault("malware", 0);
        this.phishingScore = this.breakdown.getOrDefault("phishing", 0);
        this.spamScore = this.breakdown.getOrDefault("spam", 0);
        this.piracyScore = this.breakdown.getOrDefault("piracy", 0);
        this.redirectScore = this.breakdown.getOrDefault("redirect", this.breakdown.getOrDefault("redirectRisk", 0));
        this.domainScore = this.breakdown.getOrDefault("domain", this.breakdown.getOrDefault("domainRisk", 0));
    }

    // Backward-compatible constructor used by older code paths.
    public UrlScanResponse(String status, String message, String scannedUrl, int riskScore, List<String> reasons) {
        this.status = status;
        this.verdict = status;
        this.message = message;
        this.scannedUrl = scannedUrl;
        this.finalScore = riskScore;
        this.malwareScore = 0;
        this.phishingScore = 0;
        this.spamScore = 0;
        this.piracyScore = 0;
        this.redirectScore = 0;
        this.domainScore = 0;
        this.breakdown = new LinkedHashMap<>();
        this.categoryLabels = new LinkedHashMap<>();
        this.checksPerformed = new ArrayList<>();
        this.reasons = reasons == null ? new ArrayList<>() : reasons;
        this.redirectChain = new ArrayList<>();
        this.finalUrl = scannedUrl;
        this.contactedDomains = new ArrayList<>();
        this.scriptCount = 0;
        this.totalRequests = 0;
        this.pageTitle = "";
        this.screenshotUrl = "";
    }

    // Compatibility accessor for older usages that still read riskScore.
    public int getRiskScore() {
        return finalScore;
    }

    // UI alias so frontend can read `domains` without changing backend internals.
    public List<String> getDomains() {
        return contactedDomains;
    }

    public void setDomains(List<String> domains) {
        this.contactedDomains = domains == null ? new ArrayList<>() : domains;
    }
}
