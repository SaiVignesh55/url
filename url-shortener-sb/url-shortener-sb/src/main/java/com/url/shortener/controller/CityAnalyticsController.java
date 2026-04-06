package com.url.shortener.controller;

import com.url.shortener.dtos.CityStatsByLinkDTO;
import com.url.shortener.dtos.CityStatsDTO;
import com.url.shortener.models.User;
import com.url.shortener.service.CityAnalyticsService;
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
public class CityAnalyticsController {

    private final CityAnalyticsService cityAnalyticsService;
    private final UserService userService;

    @GetMapping({"/city-stats/global", "/country-stats/global"})
    @PreAuthorize("hasRole('USER')")
    public List<CityStatsByLinkDTO> getCityStatsGlobal(Principal principal) {
        User user = userService.findByUsername(principal.getName());
        return cityAnalyticsService.getGlobalCityStats(user);
    }

    @GetMapping({"/city-stats/{shortCode:[A-Za-z0-9]{8}}", "/country-stats/{shortCode:[A-Za-z0-9]{8}}"})
    @PreAuthorize("hasRole('USER')")
    public List<CityStatsDTO> getCityStatsByShortCode(@PathVariable String shortCode, Principal principal) {
        User user = userService.findByUsername(principal.getName());
        return cityAnalyticsService.getCityStatsByShortCode(user, shortCode);
    }
}



