package com.url.shortener.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RegionStatsDTO {
    private String region;
    private Long count;
}

