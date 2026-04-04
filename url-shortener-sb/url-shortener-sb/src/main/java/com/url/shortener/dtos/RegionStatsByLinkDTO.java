package com.url.shortener.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RegionStatsByLinkDTO {
    private String shortUrl;
    private String region;
    private Long count;
}

