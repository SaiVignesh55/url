package com.url.shortener.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.url.shortener.dtos.UrlScanResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class UrlScanCacheService {

    @Value("${url.scan.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${url.scan.cache.ttl-seconds:600}")
    private long ttlSeconds;

    @Value("${url.scan.cache.max-size:500}")
    private int maxSize;

    private Cache<String, UrlScanResponse> cache;

    @PostConstruct
    void init() {
        cache = Caffeine.newBuilder()
                .maximumSize(Math.max(1, maxSize))
                .expireAfterWrite(Math.max(1, ttlSeconds), TimeUnit.SECONDS)
                .build();
    }

    public UrlScanResponse get(String key) {
        if (!cacheEnabled || key == null || key.isBlank()) {
            return null;
        }
        UrlScanResponse response = cache.getIfPresent(key);
        return response == null ? null : copyResponse(response);
    }

    public void put(String key, UrlScanResponse response) {
        if (!cacheEnabled || key == null || key.isBlank() || response == null) {
            return;
        }
        cache.put(key, copyResponse(response));
    }

    public void clear() {
        cache.invalidateAll();
    }

    private UrlScanResponse copyResponse(UrlScanResponse source) {
        UrlScanResponse copied = new UrlScanResponse(
                source.getStatus(),
                source.getMessage(),
                source.getScannedUrl(),
                source.getFinalScore(),
                source.getBreakdown() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.getBreakdown()),
                source.getCategoryLabels() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.getCategoryLabels()),
                source.getChecksPerformed() == null ? new ArrayList<>() : new ArrayList<>(source.getChecksPerformed()),
                source.getReasons() == null ? new ArrayList<>() : new ArrayList<>(source.getReasons()),
                source.getRedirectChain() == null ? new ArrayList<>() : new ArrayList<>(source.getRedirectChain()),
                source.getFinalUrl(),
                source.getContactedDomains() == null ? new ArrayList<>() : new ArrayList<>(source.getContactedDomains()),
                source.getScriptCount(),
                source.getPageTitle(),
                source.getScreenshotUrl()
        );
        copied.setTotalRequests(source.getTotalRequests());
        return copied;
    }
}
