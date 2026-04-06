package com.url.shortener.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CityStatsByLinkDTO {
    private String shortUrl;
    private String city;
    private Long count;
}



