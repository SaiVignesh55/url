package com.url.shortener.controller;

import com.url.shortener.dtos.UrlScanRequest;
import com.url.shortener.dtos.UrlScanResponse;
import com.url.shortener.dtos.UrlScanHistoryResponse;
import com.url.shortener.dtos.UrlScanAsyncStatusResponse;
import com.url.shortener.dtos.UrlScanAsyncSubmitResponse;
import com.url.shortener.service.UrlScannerService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
                    null,
                    0,
                    java.util.List.of("Please provide a valid URL in request body")
            );
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        UrlScanResponse response = urlScannerService.scanUrl(request.getUrl().trim());
        if ("INVALID_REQUEST".equals(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/async")
    public ResponseEntity<UrlScanAsyncSubmitResponse> scanUrlAsync(@RequestBody UrlScanRequest request) {
        if (request == null || request.getUrl() == null || request.getUrl().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new UrlScanAsyncSubmitResponse(null, "INVALID_REQUEST")
            );
        }

        String jobId = urlScannerService.submitAsyncScan(request.getUrl().trim());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new UrlScanAsyncSubmitResponse(jobId, "PENDING"));
    }

    @GetMapping("/async/{jobId}")
    public ResponseEntity<UrlScanAsyncStatusResponse> getAsyncScanStatus(@PathVariable String jobId) {
        return ResponseEntity.ok(urlScannerService.getAsyncScanStatus(jobId));
    }

    @GetMapping("/history")
    public ResponseEntity<List<UrlScanHistoryResponse>> getScanHistory(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(urlScannerService.getScanHistory(limit, status));
    }

    @GetMapping("/history/{id}")
    public ResponseEntity<UrlScanHistoryResponse> getScanHistoryById(@PathVariable Long id) {
        return ResponseEntity.ok(urlScannerService.getScanHistoryById(id));
    }

    @PostMapping("/cache/clear")
    public ResponseEntity<String> clearScanCache() {
        urlScannerService.clearScanCache();
        return ResponseEntity.ok("URL scan cache cleared");
    }
}

