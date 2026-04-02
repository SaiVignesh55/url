package com.url.shortener.service;

import com.url.shortener.exception.InvalidUrlException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UrlNormalizationService {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Set<String> SENSITIVE_QUERY_KEYS = Set.of(
            "token", "access_token", "auth", "apikey", "api_key", "key", "signature", "sig", "password", "secret"
    );

    @Value("${url.scan.max-length:2048}")
    private int maxUrlLength;

    public NormalizedUrl normalizeAndValidate(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new InvalidUrlException("URL is required");
        }

        String value = input.trim();
        if (value.length() > maxUrlLength) {
            throw new InvalidUrlException("URL exceeds allowed length");
        }

        if (!value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")) {
            value = "https://" + value;
        }

        URI parsed = toUri(value);
        String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new InvalidUrlException("Only http and https URLs are allowed");
        }

        String host = parsed.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("URL host is required");
        }

        if (parsed.getUserInfo() != null && !parsed.getUserInfo().isBlank()) {
            throw new InvalidUrlException("URL user info is not allowed");
        }

        String asciiHost;
        try {
            asciiHost = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            throw new InvalidUrlException("URL host is invalid");
        }

        int port = parsed.getPort();
        if (port < -1 || port > 65535) {
            throw new InvalidUrlException("URL port is invalid");
        }

        String normalizedPath = normalizePath(parsed.getRawPath());
        String normalizedQuery = normalizeQuery(parsed.getRawQuery());
        URI rebuilt = buildUri(scheme, asciiHost, port, normalizedPath, normalizedQuery);

        boolean nonAsciiHost = host.chars().anyMatch(ch -> ch > 127);
        boolean punycode = asciiHost.contains("xn--");
        boolean potentialHomograph = nonAsciiHost || punycode;
        return new NormalizedUrl(rebuilt.toString(), rebuilt, potentialHomograph, asciiHost, stripSensitiveQuery(rebuilt));
    }

    public URI toUri(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException ex) {
            throw new InvalidUrlException("Malformed URL");
        }
    }

    public String stripSensitiveQuery(URI uri) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return uri.toString();
        }

        List<String> kept = new ArrayList<>();
        for (String pair : query.split("&")) {
            String[] split = pair.split("=", 2);
            String key = decode(split[0]).toLowerCase(Locale.ROOT);
            if (!SENSITIVE_QUERY_KEYS.contains(key)) {
                kept.add(pair);
            }
        }

        String rebuiltQuery = kept.isEmpty() ? null : String.join("&", kept);
        URI rebuilt = buildUri(uri.getScheme(), uri.getHost(), uri.getPort(), uri.getRawPath(), rebuiltQuery);
        return rebuilt.toString();
    }

    private URI buildUri(String scheme, String host, int port, String path, String rawQuery) {
        try {
            return new URI(scheme, null, host, port, path, rawQuery, null).normalize();
        } catch (URISyntaxException ex) {
            throw new InvalidUrlException("Malformed URL after normalization");
        }
    }

    private String normalizePath(String rawPath) {
        String path = (rawPath == null || rawPath.isBlank()) ? "/" : rawPath;
        String collapsed = Arrays.stream(path.split("/", -1))
                .map(this::encodePathSegment)
                .collect(Collectors.joining("/"));
        return collapsed.startsWith("/") ? collapsed : "/" + collapsed;
    }

    private String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String pair : rawQuery.split("&")) {
            String[] split = pair.split("=", 2);
            String key = UriComponentsBuilder.newInstance().queryParam(decode(split[0])).build().getQueryParams().keySet().iterator().next();
            if (split.length == 2) {
                parts.add(key + "=" + encodeQueryValue(decode(split[1])));
            } else {
                parts.add(key);
            }
        }
        return String.join("&", parts);
    }

    private String encodePathSegment(String value) {
        return value;
    }

    private String encodeQueryValue(String value) {
        return UriComponentsBuilder.newInstance().queryParam("v", value).build().toUriString().split("=", 2)[1];
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    public record NormalizedUrl(String normalizedUrl,
                                URI uri,
                                boolean potentialHomograph,
                                String asciiHost,
                                String urlscanSafeUrl) {
    }
}

