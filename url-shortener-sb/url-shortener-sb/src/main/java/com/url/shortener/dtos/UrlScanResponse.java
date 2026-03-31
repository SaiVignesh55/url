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
    private String message;
    private String scannedUrl;
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
    private String pageTitle;
    private String screenshotUrl;

    // Backward-compatible constructor used by older code paths.
    public UrlScanResponse(String status, String message, String scannedUrl, int riskScore, List<String> reasons) {
        this.status = status;
        this.message = message;
        this.scannedUrl = scannedUrl;
        this.finalScore = riskScore;
        this.breakdown = new LinkedHashMap<>();
        this.categoryLabels = new LinkedHashMap<>();
        this.checksPerformed = new ArrayList<>();
        this.reasons = reasons == null ? new ArrayList<>() : reasons;
        this.redirectChain = new ArrayList<>();
        this.finalUrl = scannedUrl;
        this.contactedDomains = new ArrayList<>();
        this.scriptCount = 0;
        this.pageTitle = "";
        this.screenshotUrl = "";
    }

    // Compatibility accessor for older usages that still read riskScore.
    public int getRiskScore() {
        return finalScore;
    }
}
