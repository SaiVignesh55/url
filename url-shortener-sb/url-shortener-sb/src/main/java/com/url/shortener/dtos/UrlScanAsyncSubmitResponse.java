package com.url.shortener.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UrlScanAsyncSubmitResponse {
    private String jobId;
    private String status;
}

