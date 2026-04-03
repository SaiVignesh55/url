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
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ipAddress = (forwardedFor != null && !forwardedFor.isBlank())
                ? forwardedFor.split(",")[0].trim()
                : request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        UrlMapping urlMapping = urlMappingService.getOriginalUrl(code, ipAddress, userAgent);
        if (urlMapping != null) {
            return ResponseEntity.status(302)
                    .location(URI.create(urlMapping.getOriginalUrl()))
                    .build();
        }

        return ResponseEntity.notFound().build();
    }
}