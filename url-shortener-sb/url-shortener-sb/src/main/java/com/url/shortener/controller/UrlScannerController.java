package com.url.shortener.controller;

import com.url.shortener.dtos.UrlScanAsyncStatusResponse;
import com.url.shortener.dtos.UrlScanAsyncSubmitResponse;
import com.url.shortener.dtos.UrlScanRequest;
import com.url.shortener.dtos.UrlScanResponse;
import com.url.shortener.service.UrlScannerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class UrlScannerController {

    private static final Logger log = LoggerFactory.getLogger(UrlScannerController.class);

    private final UrlScannerService urlScannerService;

    @PostMapping("/scan")
    public ResponseEntity<UrlScanAsyncSubmitResponse> scanUrl(@Valid @RequestBody UrlScanRequest request,
                                                               HttpServletRequest servletRequest) {
        String requestedUrl = request.getUrl().trim();
        String ipAddress = extractClientIp(servletRequest);
        log.info("IP DETECTED: {}", ipAddress);
        log.info("Received scan request for url={}", requestedUrl);
        System.out.println("SCAN REQUEST RECEIVED: " + requestedUrl);

        String scanId = urlScannerService.submitAsyncScan(requestedUrl, ipAddress);
        UrlScanAsyncSubmitResponse response = new UrlScanAsyncSubmitResponse(scanId, "IN_PROGRESS");

        log.info("Returning scan submit response: scanId={}, status={}", response.getScanId(), response.getStatus());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/status/{scanId}")
    public ResponseEntity<UrlScanAsyncStatusResponse> getScanStatus(@PathVariable String scanId) {
        log.info("Received status request for scanId={}", scanId);

        UrlScanAsyncStatusResponse asyncStatus = urlScannerService.getAsyncScanStatus(scanId);
        UrlScanResponse result = asyncStatus.getResult();

        String status;
        if ("PENDING".equalsIgnoreCase(asyncStatus.getStatus())) {
            status = "IN_PROGRESS";
        } else if (result != null && isTerminalStatus(result.getStatus())) {
            status = result.getStatus();
        } else if (result != null && result.getStatus() != null && !result.getStatus().isBlank()) {
            status = result.getStatus();
        } else {
            status = "UNKNOWN";
        }

        UrlScanAsyncStatusResponse response = new UrlScanAsyncStatusResponse(scanId, status, result, asyncStatus.getErrorMessage());
        log.info("Returning status response: scanId={}, status={}", response.getScanId(), response.getStatus());

        return ResponseEntity.ok(response);
    }


    @PostMapping("/scan/cache/clear")
    public ResponseEntity<String> clearScanCache() {
        urlScannerService.clearScanCache();
        return ResponseEntity.ok("URL scan cache cleared");
    }

    private boolean isTerminalStatus(String status) {
        if (status == null) {
            return false;
        }
        return "SAFE".equalsIgnoreCase(status)
                || "MALICIOUS".equalsIgnoreCase(status)
                || "SUSPICIOUS".equalsIgnoreCase(status)
                || "UNKNOWN".equalsIgnoreCase(status);
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
