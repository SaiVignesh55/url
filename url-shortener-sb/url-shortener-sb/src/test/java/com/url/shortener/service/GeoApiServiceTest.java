package com.url.shortener.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.url.shortener.dtos.GeoData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GeoApiServiceTest {

    private RestTemplate restTemplate;
    private GeoApiService geoApiService;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        geoApiService = new GeoApiService(new ObjectMapper(), restTemplate);
    }

    @Test
    void shouldSkipApiCallForPrivateIp() {
        GeoData result = geoApiService.getRegionFromIp("10.0.0.25");

        assertEquals("UNKNOWN", result.getCountry());
        assertEquals("UNKNOWN", result.getRegion());
        assertEquals("UNKNOWN", result.getCity());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void shouldReturnUnknownWhenIpApiReturnsErrorPayload() {
        when(restTemplate.getForObject(eq("http://ip-api.com/json/8.8.8.8"), eq(String.class)))
                .thenReturn("{\"status\":\"fail\",\"message\":\"private range\"}");

        GeoData result = geoApiService.getRegionFromIp("8.8.8.8");

        assertEquals("UNKNOWN", result.getCountry());
        assertEquals("UNKNOWN", result.getRegion());
        assertEquals("UNKNOWN", result.getCity());
    }

    @Test
    void shouldFallbackCityToUnknownWhenCityIsNull() {
        when(restTemplate.getForObject(eq("http://ip-api.com/json/8.8.8.8"), eq(String.class)))
                .thenReturn("{\"status\":\"success\",\"country\":\"United States\",\"regionName\":\"California\",\"city\":null}");

        GeoData result = geoApiService.getRegionFromIp("8.8.8.8");

        assertEquals("United States", result.getCountry());
        assertEquals("California", result.getRegion());
        assertEquals("UNKNOWN", result.getCity());
    }

    @Test
    void shouldReturnUnknownWhenIpApiThrowsException() {
        when(restTemplate.getForObject(eq("http://ip-api.com/json/8.8.8.8"), eq(String.class)))
                .thenThrow(new RuntimeException("timeout"));

        GeoData result = geoApiService.getRegionFromIp("8.8.8.8");

        assertEquals("UNKNOWN", result.getCountry());
        assertEquals("UNKNOWN", result.getRegion());
        assertEquals("UNKNOWN", result.getCity());
        verify(restTemplate).getForObject(eq("http://ip-api.com/json/8.8.8.8"), eq(String.class));
    }
}

