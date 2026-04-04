package com.url.shortener.controller;

import com.url.shortener.dtos.GeoDebugResponse;
import com.url.shortener.service.GeoApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class GeoDebugController {

    private final GeoApiService geoApiService;

    @GetMapping("/region")
    @PreAuthorize("hasRole('ADMIN')")
    public GeoDebugResponse debugRegion(@RequestParam("url") String url) {
        return geoApiService.debugRegionFromUrl(url);
    }
}

