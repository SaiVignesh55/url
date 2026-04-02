package com.url.shortener.dtos;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class UrlScanRequest {
    @NotBlank(message = "URL is required")
    @Size(max = 2048, message = "URL exceeds allowed length")
    private String url;
}

