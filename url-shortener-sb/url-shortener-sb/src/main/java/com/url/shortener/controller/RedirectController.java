package com.url.shortener.controller;

import com.url.shortener.models.UrlMapping;
import com.url.shortener.service.UrlMappingService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;

@RestController
@AllArgsConstructor
public class RedirectController {

    private UrlMappingService urlMappingService;

    @GetMapping("/r/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request){
        return trackAndRedirect(code, request);
    }

    @GetMapping("/{code:[A-Za-z0-9]{8}}")
    public ResponseEntity<Void> redirectByShortCode(@PathVariable String code, HttpServletRequest request) {
        return trackAndRedirect(code, request);
    }

    private ResponseEntity<Void> trackAndRedirect(String code, HttpServletRequest request) {
        String ipAddress = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        UrlMapping urlMapping = urlMappingService.getOriginalUrl(code, ipAddress, userAgent);
        if (urlMapping != null) {
            return ResponseEntity.status(302)
                    .location(URI.create(urlMapping.getOriginalUrl()))
                    .build();
        }

        return ResponseEntity.notFound().build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String[] headerNames = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Forwarded"
        };

        for (String headerName : headerNames) {
            String headerValue = request.getHeader(headerName);
            if (headerValue != null && !headerValue.isBlank()) {
                String candidate = extractFirstIpToken(headerValue);
                if (candidate != null) {
                    return candidate;
                }
            }
        }

        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr == null || remoteAddr.isBlank()) ? null : remoteAddr.trim();
    }

    private String extractFirstIpToken(String headerValue) {
        String[] parts = headerValue.split(",");
        for (String part : parts) {
            String candidate = part.trim();
            if (candidate.startsWith("for=")) {
                candidate = candidate.substring(4).trim();
            }
            int semicolonIndex = candidate.indexOf(';');
            if (semicolonIndex > 0) {
                candidate = candidate.substring(0, semicolonIndex).trim();
            }
            if (candidate.startsWith("\"") && candidate.endsWith("\"") && candidate.length() > 1) {
                candidate = candidate.substring(1, candidate.length() - 1);
            }
            if (!candidate.isBlank() && !"unknown".equalsIgnoreCase(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}