package com.url.shortener.service;

import com.url.shortener.dtos.UrlScanResponse;
import org.springframework.stereotype.Service;

@Service
public class UrlScannerService {

    public UrlScanResponse scanUrl(String url) {
        // Phase 1: return a simple placeholder response to test request flow.
        return new UrlScanResponse(
                "RECEIVED",
                "URL received. Real safety scan will be added in Phase 2.",
                url
        );
    }
}

