package com.url.shortener.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class UrlScanResponse {
    private String status;
    private String message;
    private String scannedUrl;
    private int riskScore;
    private List<String> reasons;
}

