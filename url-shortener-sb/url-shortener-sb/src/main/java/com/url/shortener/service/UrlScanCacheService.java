package com.url.shortener.service;

import com.url.shortener.dtos.UrlScanResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UrlScanCacheService {

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Value("${url.scan.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${url.scan.cache.ttl-seconds:600}")
    private long ttlSeconds;

    @Value("${url.scan.cache.max-size:500}")
    private int maxSize;

    public UrlScanResponse get(String key) {
        if (!cacheEnabled || key == null || key.isBlank()) {
            return null;
        }

        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }

        if (isExpired(entry)) {
            cache.remove(key);
            return null;
        }

        return copyResponse(entry.response());
    }

    public void put(String key, UrlScanResponse response) {
        if (!cacheEnabled || key == null || key.isBlank() || response == null || ttlSeconds <= 0) {
            return;
        }

        if (cache.size() >= Math.max(maxSize, 1)) {
            evictOneEntry();
        }

        long expiresAt = System.currentTimeMillis() + (ttlSeconds * 1000);
        cache.put(key, new CacheEntry(copyResponse(response), expiresAt));
    }

    public void clear() {
        cache.clear();
    }

    private boolean isExpired(CacheEntry entry) {
        return System.currentTimeMillis() > entry.expiresAtEpochMillis();
    }

    private void evictOneEntry() {
        cache.keySet().stream().findFirst().ifPresent(cache::remove);
    }

    private UrlScanResponse copyResponse(UrlScanResponse source) {
        return new UrlScanResponse(
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
    }

    private record CacheEntry(UrlScanResponse response, long expiresAtEpochMillis) {
    }
}
