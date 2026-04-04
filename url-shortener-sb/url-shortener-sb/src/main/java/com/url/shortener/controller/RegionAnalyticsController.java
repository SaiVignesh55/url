package com.url.shortener.controller;

import com.url.shortener.dtos.RegionStatsByLinkDTO;
import com.url.shortener.dtos.RegionStatsDTO;
import com.url.shortener.models.User;
import com.url.shortener.service.RegionAnalyticsService;
import com.url.shortener.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RegionAnalyticsController {

    private final RegionAnalyticsService regionAnalyticsService;
    private final UserService userService;

    @GetMapping("/region-stats/global")
    @PreAuthorize("hasRole('USER')")
    public List<RegionStatsByLinkDTO> getRegionStatsGlobal(Principal principal) {
        User user = userService.findByUsername(principal.getName());
        return regionAnalyticsService.getGlobalRegionStats(user);
    }

    @GetMapping("/region-stats/{shortCode:[A-Za-z0-9]{8}}")
    @PreAuthorize("hasRole('USER')")
    public List<RegionStatsDTO> getRegionStatsByShortCode(@PathVariable String shortCode, Principal principal) {
        User user = userService.findByUsername(principal.getName());
        return regionAnalyticsService.getRegionStatsByShortCode(user, shortCode);
    }
}

