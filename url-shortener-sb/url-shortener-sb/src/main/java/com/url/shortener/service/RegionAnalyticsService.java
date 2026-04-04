package com.url.shortener.service;

import com.url.shortener.dtos.RegionStatsByLinkDTO;
import com.url.shortener.dtos.RegionStatsDTO;
import com.url.shortener.models.User;
import com.url.shortener.repository.ClickEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RegionAnalyticsService {

    private final ClickEventRepository clickEventRepository;

    public List<RegionStatsDTO> getRegionStatsByShortCode(User user, String shortCode) {
        return clickEventRepository.aggregateRegionCountsByShortCode(user, shortCode)
                .stream()
                .map(item -> new RegionStatsDTO(item.getRegion(), item.getCount()))
                .toList();
    }

    public List<RegionStatsByLinkDTO> getGlobalRegionStats(User user) {
        return clickEventRepository.aggregateRegionCountsGlobalByUser(user)
                .stream()
                .map(item -> new RegionStatsByLinkDTO(item.getShortUrl(), item.getRegion(), item.getCount()))
                .toList();
    }
}

