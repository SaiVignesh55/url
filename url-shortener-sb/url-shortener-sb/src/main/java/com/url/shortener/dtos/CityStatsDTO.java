package com.url.shortener.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CityStatsDTO {
    private String city;
    private Long count;
}



