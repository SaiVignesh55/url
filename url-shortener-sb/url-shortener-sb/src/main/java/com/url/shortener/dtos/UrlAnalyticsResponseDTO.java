package com.url.shortener.dtos;

import lombok.Data;

import java.util.List;

@Data
public class UrlAnalyticsResponseDTO {
    private String shortCode;
    private String originalUrl;
    private Long totalClicks;
    private List<AnalyticsPointDTO> clicksOverTime;
}

