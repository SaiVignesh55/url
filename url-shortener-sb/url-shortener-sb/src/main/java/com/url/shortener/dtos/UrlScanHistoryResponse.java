package com.url.shortener.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class UrlScanHistoryResponse {
    private Long id;
    private String scannedUrl;
    private String status;
    private String message;
    private int riskScore;
    private List<String> reasons;
    private LocalDateTime createdAt;
}

