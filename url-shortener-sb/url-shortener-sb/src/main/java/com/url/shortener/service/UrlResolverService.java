package com.url.shortener.service;

import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

@Service
public class UrlResolverService {

    private static final int MAX_REDIRECTS = 5;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    public String resolveFinalUrl(String inputUrl) {
        if (inputUrl == null || inputUrl.isBlank()) {
            return inputUrl;
        }

        String current = inputUrl.trim();
        Set<String> visited = new HashSet<>();

        for (int i = 0; i < MAX_REDIRECTS; i++) {
            if (!visited.add(current)) {
                // Stop on redirect loops and use the latest known URL.
                return current;
            }

            String location = readLocationHeader(current);
            if (location == null || location.isBlank()) {
                // No redirect header means this is the final URL.
                return current;
            }

            current = toAbsoluteUrl(current, location);
        }

        return current;
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
}

