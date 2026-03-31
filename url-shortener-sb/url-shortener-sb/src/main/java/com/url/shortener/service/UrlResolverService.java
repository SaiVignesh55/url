package com.url.shortener.service;

import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UrlResolverService {

    private static final int MAX_REDIRECTS = 5;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    public ResolvedResult resolveUrl(String inputUrl) {
        String current = normalize(inputUrl);
        if (current.isBlank()) {
            return new ResolvedResult(current, List.of());
        }

        List<String> chain = new ArrayList<>();
        chain.add(current);
        Set<String> visited = new HashSet<>();
        visited.add(current);

        for (int i = 0; i < MAX_REDIRECTS; i++) {
            String location = readLocationHeader(current);
            if (location == null || location.isBlank()) {
                return new ResolvedResult(current, chain);
            }

            String next = normalize(toAbsoluteUrl(current, location));
            if (next.isBlank()) {
                return new ResolvedResult(current, chain);
            }

            chain.add(next);
            if (!visited.add(next)) {
                return new ResolvedResult(next, chain);
            }

            current = next;
        }

        return new ResolvedResult(current, chain);
    }

    public String resolveFinalUrl(String inputUrl) {
        return resolveUrl(inputUrl).finalUrl();
    }

    private String readLocationHeader(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "UrlScanner/1.0");

            int status = connection.getResponseCode();
            if (isRedirectStatus(status)) {
                return connection.getHeaderField("Location");
            }
            return null;
        } catch (Exception ex) {
            // Fail safe: scanner should continue even if resolver cannot resolve.
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean isRedirectStatus(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307
                || status == 308;
    }

    private String toAbsoluteUrl(String baseUrl, String location) {
        try {
            URI base = URI.create(baseUrl);
            URI next = base.resolve(location);
            return next.toString();
        } catch (Exception ex) {
            return location;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim();
        if (!normalized.isBlank()
                && !normalized.toLowerCase().startsWith("http://")
                && !normalized.toLowerCase().startsWith("https://")) {
            normalized = "https://" + normalized;
        }

        if (normalized.endsWith("/") && normalized.length() > "https://".length()) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public record ResolvedResult(String finalUrl, List<String> chain) {
    }
}

