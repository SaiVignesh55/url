package com.url.shortener.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UrlScanAsyncStatusResponse {
    private String scanId;
    private String status;
    private UrlScanResponse result;
    private String errorMessage;
}
