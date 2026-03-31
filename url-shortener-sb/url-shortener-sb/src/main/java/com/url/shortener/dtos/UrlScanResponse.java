package com.url.shortener.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UrlScanResponse {
    private String status;
    private String message;
    private String scannedUrl;
}

