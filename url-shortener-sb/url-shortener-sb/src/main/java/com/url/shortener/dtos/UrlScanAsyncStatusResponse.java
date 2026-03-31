package com.url.shortener.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UrlScanAsyncStatusResponse {
    private String jobId;
    private String status;
    private UrlScanResponse result;
    private String errorMessage;
}

