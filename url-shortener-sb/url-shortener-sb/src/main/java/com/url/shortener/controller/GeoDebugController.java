package com.url.shortener.controller;

import com.url.shortener.dtos.GeoDebugResponse;
import com.url.shortener.security.IpAddressResolver;
import com.url.shortener.service.GeoApiService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class GeoDebugController {

    private final GeoApiService geoApiService;
    private final IpAddressResolver ipAddressResolver;

    @GetMapping("/region")
    @PreAuthorize("hasRole('ADMIN')")
    public GeoDebugResponse debugRegion(@RequestParam("url") String url) {
        return geoApiService.debugRegionFromUrl(url);
    }

    @GetMapping("/test-ip")
    public String testIp(HttpServletRequest request) {
        String extractedIp = ipAddressResolver.getClientIp(request);
        return "IP: " + extractedIp;
    }

    @GetMapping("/test-ipapi")
    public String testIpApi() {
        return geoApiService.fetchIpApiRawForTest("8.8.8.8");
    }

    @GetMapping("/debug-ip")
    public Map<String, Object> debugIp(@RequestParam String ip) {
        Map<String, Object> result = new HashMap<>();
        result.put("input_ip", ip);
        String rawResponse = geoApiService.fetchIpApiRawForTest(ip);
        String city = geoApiService.getCityFromIp(ip);
        result.put("raw_response", rawResponse);
        result.put("parsed_city", city);
        result.put("status", "UNKNOWN".equals(city) ? "FAILED - NO CITY" : "SUCCESS");
        return result;
    }

    @GetMapping("/debug-ipapi")
    public Map<String, String> debugIpApi(HttpServletRequest request) {
        Map<String, String> result = new HashMap<>();
        String ip = ipAddressResolver.getClientIp(request);
        String city = geoApiService.getCityFromIp(ip);

        result.put("ip", ip);
        result.put("city", city);
        return result;
    }

    @GetMapping("/debug-click")
    public Map<String, Object> debugClick(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }

        result.put("detected_ip", ip);
        System.out.println("==== IP DEBUG START ====");
        System.out.println("X-Forwarded-For: " + request.getHeader("X-Forwarded-For"));
        System.out.println("X-Real-IP: " + request.getHeader("X-Real-IP"));
        System.out.println("RemoteAddr: " + request.getRemoteAddr());
        System.out.println("detected IP: " + ip);
        System.out.println("==== IP DEBUG END ====");

        return result;
    }
}

