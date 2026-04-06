package com.url.shortener.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class ScanRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ScanRateLimitFilter.class);

    private final IpAddressResolver ipAddressResolver;

    @Value("${url.scan.rate-limit.ip-per-minute:60}")
    private int ipPerMinute;

    @Value("${url.scan.rate-limit.user-per-minute:30}")
    private int userPerMinute;

    @Value("${url.scan.quota.user-per-day:1000}")
    private int userPerDay;

    private final Map<String, FixedWindowCounter> ipCounters = new ConcurrentHashMap<>();
    private final Map<String, FixedWindowCounter> userCounters = new ConcurrentHashMap<>();
    private final Map<String, DailyQuotaCounter> quotaCounters = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !("POST".equalsIgnoreCase(request.getMethod()) && path.startsWith("/api/scan"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = extractIp(request);
        log.debug("ScanRateLimitFilter entered for {} {} from ip={}", request.getMethod(), request.getRequestURI(), ip);

        if (isLocalRequest(ip)) {
            log.debug("Bypassing rate-limit filter for local request ip={}", ip);
            filterChain.doFilter(request, response);
            return;
        }

        if (!allow(ipCounters, "ip:" + ip, ipPerMinute)) {
            log.warn("Rate limit blocked request by IP: ip={}, path={}", ip, request.getRequestURI());
            reject(response, "IP rate limit exceeded");
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String user = authentication == null ? null : authentication.getName();
        if (user == null || user.isBlank() || "anonymousUser".equals(user)) {
            log.debug("Anonymous scanner request allowed with IP-only rate limiting, ip={}", ip);
            filterChain.doFilter(request, response);
            return;
        }

        if (!allow(userCounters, "user:" + user, userPerMinute)) {
            log.warn("Rate limit blocked request by user: user={}, path={}", user, request.getRequestURI());
            reject(response, "User rate limit exceeded");
            return;
        }

        if (!allowDailyQuota(user)) {
            log.warn("Daily quota blocked request: user={}, path={}", user, request.getRequestURI());
            reject(response, "Daily scan quota exceeded");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean allow(Map<String, FixedWindowCounter> counters, String key, int limit) {
        if (limit <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        long windowStart = now - (now % 60000);

        FixedWindowCounter counter = counters.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStart != windowStart) {
                return new FixedWindowCounter(windowStart);
            }
            return existing;
        });

        return counter.count.incrementAndGet() <= limit;
    }

    private boolean allowDailyQuota(String user) {
        LocalDate today = LocalDate.now();
        DailyQuotaCounter counter = quotaCounters.compute(user, (k, existing) -> {
            if (existing == null || !existing.day.equals(today)) {
                return new DailyQuotaCounter(today);
            }
            return existing;
        });
        return counter.count.incrementAndGet() <= Math.max(1, userPerDay);
    }

    private String extractIp(HttpServletRequest request) {
        return ipAddressResolver.resolveClientIp(request);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }

    private boolean isLocalRequest(String ip) {
        if (ip == null) {
            return false;
        }
        String normalized = ip.trim();
        return "127.0.0.1".equals(normalized)
                || "0:0:0:0:0:0:0:1".equals(normalized)
                || "::1".equals(normalized)
                || "localhost".equalsIgnoreCase(normalized);
    }

    private static class FixedWindowCounter {
        private final long windowStart;
        private final AtomicInteger count = new AtomicInteger(0);

        private FixedWindowCounter(long windowStart) {
            this.windowStart = windowStart;
        }
    }

    private static class DailyQuotaCounter {
        private final LocalDate day;
        private final AtomicInteger count = new AtomicInteger(0);

        private DailyQuotaCounter(LocalDate day) {
            this.day = day;
        }
    }

    @Scheduled(fixedDelayString = "${url.scan.rate-limit.cleanup-ms:300000}")
    public void cleanup() {
        long currentWindow = System.currentTimeMillis() - (System.currentTimeMillis() % 60000);
        ipCounters.entrySet().removeIf(entry -> entry.getValue().windowStart < currentWindow);
        userCounters.entrySet().removeIf(entry -> entry.getValue().windowStart < currentWindow);
        LocalDate today = LocalDate.now();
        quotaCounters.entrySet().removeIf(entry -> !entry.getValue().day.equals(today));
    }
}

