package com.url.shortener.controller;

import com.url.shortener.dtos.QrCodeResponseDTO;
import com.url.shortener.models.UrlMapping;
import com.url.shortener.models.User;
import com.url.shortener.service.QrCodeService;
import com.url.shortener.service.UrlMappingService;
import com.url.shortener.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/qr")
@RequiredArgsConstructor
public class QrCodeController {

    private final UrlMappingService urlMappingService;
    private final UserService userService;
    private final QrCodeService qrCodeService;

    @GetMapping("/{shortCode}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<QrCodeResponseDTO> getQrCode(@PathVariable String shortCode, Principal principal) {
        User user = userService.findByUsername(principal.getName());
        UrlMapping mapping = urlMappingService.getUrlByShortCodeForUser(shortCode, user)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Short URL not found"));

        String shortUrl = buildShortUrl(mapping.getShortUrl());
        String base64Png = qrCodeService.generateQRCodeBase64(shortUrl);

        QrCodeResponseDTO response = new QrCodeResponseDTO();
        response.setShortCode(mapping.getShortUrl());
        response.setShortUrl(shortUrl);
        response.setMimeType("image/png");
        response.setBase64Png(base64Png);

        return ResponseEntity.ok(response);
    }

    private String buildShortUrl(String shortCode) {
        String baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }

        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String shortUrl = normalizedBase + "/r/" + shortCode;
        System.out.println("Generated URL: " + shortUrl);
        return shortUrl;
    }
}

