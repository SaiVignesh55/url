package com.url.shortener.controller;

import com.url.shortener.dtos.UrlScanRequest;
import com.url.shortener.dtos.UrlScanResponse;
import com.url.shortener.service.UrlScannerService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scan")
@AllArgsConstructor
public class UrlScannerController {

    private UrlScannerService urlScannerService;

    @PostMapping
    public ResponseEntity<UrlScanResponse> scanUrl(@RequestBody UrlScanRequest request) {
        if (request == null || request.getUrl() == null || request.getUrl().trim().isEmpty()) {
            UrlScanResponse errorResponse = new UrlScanResponse(
                    "INVALID_REQUEST",
                    "URL is required",
                    null
            );
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        UrlScanResponse response = urlScannerService.scanUrl(request.getUrl().trim());
        return ResponseEntity.ok(response);
    }
}

