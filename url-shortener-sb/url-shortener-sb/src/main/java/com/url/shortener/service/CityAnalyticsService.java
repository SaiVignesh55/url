package com.url.shortener.service;

import com.url.shortener.dtos.CityStatsByLinkDTO;
import com.url.shortener.dtos.CityStatsDTO;
import com.url.shortener.models.User;
import com.url.shortener.repository.ClickEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CityAnalyticsService {

    private final ClickEventRepository clickEventRepository;

    public List<CityStatsDTO> getCityStatsByShortCode(User user, String shortCode) {
        return clickEventRepository.aggregateCityCountsByShortCode(user, shortCode)
                .stream()
                .map(item -> new CityStatsDTO(normalizeCity(item.getCity()), item.getCount()))
                .toList();
    }

    public List<CityStatsByLinkDTO> getGlobalCityStats(User user) {
        return clickEventRepository.aggregateCityCountsGlobalByUser(user)
                .stream()
                .map(item -> new CityStatsByLinkDTO(item.getShortUrl(), normalizeCity(item.getCity()), item.getCount()))
                .toList();
    }

    private String normalizeCity(String city) {
        return (city == null || city.isBlank()) ? "UNKNOWN" : city;
    }
}



