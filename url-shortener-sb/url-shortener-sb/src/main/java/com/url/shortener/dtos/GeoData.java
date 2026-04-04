package com.url.shortener.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeoData {
    private String country;
    private String region;
    private String city;

    public static GeoData unknown() {
        return new GeoData("UNKNOWN", "UNKNOWN", "UNKNOWN");
    }
}

