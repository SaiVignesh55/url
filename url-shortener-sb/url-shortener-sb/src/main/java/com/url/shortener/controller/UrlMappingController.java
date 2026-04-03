package com.url.shortener.controller;

import com.url.shortener.dtos.AnalyticsResponseDTO;
import com.url.shortener.dtos.UrlAnalyticsResponseDTO;
import com.url.shortener.dtos.UrlMappingDTO;
import com.url.shortener.models.User;
import com.url.shortener.service.UrlMappingService;
import com.url.shortener.service.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/urls")
@AllArgsConstructor
@Slf4j
public class UrlMappingController {
    private UrlMappingService urlMappingService;
    private UserService userService;

    // {"originalUrl":"https://example.com"}
//    https://abc.com/QN7XOa0a --> https://example.com

    @PostMapping("/shorten")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UrlMappingDTO> createShortUrl(@RequestBody Map<String, String> request,
                                                        Principal principal){
        String originalUrl = request.get("originalUrl");
        User user = userService.findByUsername(principal.getName());
        UrlMappingDTO urlMappingDTO = urlMappingService.createShortUrl(originalUrl, user);
        return ResponseEntity.ok(urlMappingDTO);
    }


    @GetMapping("/myurls")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<UrlMappingDTO>> getUserUrls(Principal principal){
        User user = userService.findByUsername(principal.getName());
        List<UrlMappingDTO> urls = urlMappingService.getUrlsByUser(user);
        return ResponseEntity.ok(urls);
    }


    @GetMapping("/analytics/{shortUrl}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getUrlAnalytics(
            @PathVariable String shortUrl,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate
    ){
        LocalDate defaultEndDate = LocalDate.now();
        LocalDate resolvedEndDate = parseDateOrDefault(endDate, defaultEndDate, "endDate");
        LocalDate resolvedStartDate = parseDateOrDefault(startDate, resolvedEndDate.minusDays(29), "startDate");

        try {
            UrlAnalyticsResponseDTO response = urlMappingService.getUrlAnalyticsByShortCode(shortUrl, resolvedStartDate, resolvedEndDate);
            if (response == null) {
                return ResponseEntity.status(404).body(errorBody("Short URL not found"));
            }

            if (response.getClicksOverTime() == null) {
                response.setClicksOverTime(List.of());
            }
            if (response.getTotalClicks() == null) {
                response.setTotalClicks(0L);
            }

            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("Analytics request failed shortUrl={}", shortUrl, ex);
            return ResponseEntity.status(500).body(errorBody("Analytics processing failed"));
        }
    }


    @GetMapping("/totalClicks")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AnalyticsResponseDTO> getTotalClicksByDate(Principal principal,
                                                                     @RequestParam(value = "startDate", required = false) String startDate,
                                                                     @RequestParam(value = "endDate", required = false) String endDate){
        User user = userService.findByUsername(principal.getName());
        LocalDate defaultEndDate = LocalDate.now();
        LocalDate resolvedEndDate = parseDateOrDefault(endDate, defaultEndDate, "endDate");
        LocalDate resolvedStartDate = parseDateOrDefault(startDate, resolvedEndDate.minusDays(29), "startDate");
        AnalyticsResponseDTO totalClicks = urlMappingService.getTotalClicksByUserAndDate(user, resolvedStartDate, resolvedEndDate);
        return ResponseEntity.ok(totalClicks);
    }

    private LocalDate parseDateOrDefault(String value, LocalDate defaultValue, String parameterName) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        String normalizedValue = value.length() >= 10 ? value.substring(0, 10) : value;
        try {
            return LocalDate.parse(normalizedValue);
        } catch (Exception ex) {
            log.warn("Invalid {} received: {}. Falling back to {}", parameterName, value, defaultValue);
            return defaultValue;
        }
    }

    private Map<String, String> errorBody(String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", message);
        return body;
    }
}