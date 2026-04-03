package com.url.shortener.dtos;

import lombok.Data;

import java.util.List;

@Data
public class AnalyticsResponseDTO {
    private Long totalClicks;
    private List<ClickEventDTO> clicksByDate;
}

