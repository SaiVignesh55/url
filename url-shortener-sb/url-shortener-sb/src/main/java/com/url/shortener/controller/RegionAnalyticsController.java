package com.url.shortener.controller;

import com.url.shortener.dtos.RegionStatsDTO;
import com.url.shortener.models.User;
import com.url.shortener.repository.ClickEventRepository;
import com.url.shortener.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RegionAnalyticsController {

    private final ClickEventRepository clickEventRepository;
    private final UserService userService;

    @GetMapping("/region-stats")
    @PreAuthorize("hasRole('USER')")
    public List<RegionStatsDTO> getRegionStats(Principal principal) {
        User user = userService.findByUsername(principal.getName());
        return clickEventRepository.aggregateRegionCountsByUser(user)
                .stream()
                .map(item -> new RegionStatsDTO(item.getRegion(), item.getCount()))
                .toList();
    }
}

