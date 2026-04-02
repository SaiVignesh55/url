package com.url.shortener.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UrlScanAsyncSubmitResponse {
    private String scanId;
    private String status;
}
