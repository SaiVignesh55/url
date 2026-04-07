package com.url.shortener.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.url.shortener.dtos.GeoDebugResponse;
import com.url.shortener.dtos.GeoData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class GeoApiService {

    private static final String IPAPI_URL = "https://ipapi.co/{ip}/json/";

    @Value("${ipapi.key:}")
    private String apiKey;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Autowired
    public GeoApiService(ObjectMapper objectMapper, @Qualifier("scannerRestTemplate") RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public GeoData getRegionFromUrl(String url) {
        if (url == null || url.isBlank()) {
            log.error("Region lookup failed: original URL is null/blank");
            return GeoData.unknown();
        }

        try {
            log.info("URL: {}", url);
            String domain = extractDomain(url);
            log.info("DOMAIN: {}", domain);

            String resolvedIp = resolveIp(domain);
            log.info("RESOLVED IP: {}", resolvedIp);

            if (resolvedIp == null || resolvedIp.isBlank() || isLoopbackIp(resolvedIp)) {
                log.error("Region lookup failed: invalid resolved IP={} for domain={}", resolvedIp, domain);
                return GeoData.unknown();
            }

            GeoData geoData = getRegionByIp(resolvedIp);
            log.info("REGION: {}", geoData.getRegion());
            return geoData;
        } catch (Exception ex) {
            log.warn("Failed to resolve region from url={}: {}", url, ex.getMessage());
            return GeoData.unknown();
        }
    }

    public GeoData getRegionFromIp(String ipAddress) {
        String normalizedIp = normalizeIp(ipAddress);
        if (normalizedIp == null || normalizedIp.isBlank()) {
            log.warn("Region lookup skipped: client IP is missing or invalid");
            return GeoData.unknown();
        }

        String ipForLookup = normalizedIp;
        if (isLoopbackIp(ipForLookup) || isPrivateIP(ipForLookup)) {
            log.warn("Client IP is local/private. Using fallback public IP for ipapi lookup. originalIp={} fallbackIp=8.8.8.8", normalizedIp);
            ipForLookup = "8.8.8.8";
        }

        GeoData geoData = getRegionByIp(ipForLookup);
        log.info("CLIENT IP GEO: ip={} country={} region={} city={}",
                ipForLookup,
                geoData.getCountry(),
                geoData.getRegion(),
                geoData.getCity());
        return geoData;
    }

    public GeoDebugResponse debugRegionFromUrl(String url) {
        GeoDebugResponse debug = new GeoDebugResponse();
        debug.setUrl(url);
        debug.setSuccess(false);

        if (url == null || url.isBlank()) {
            debug.setFallbackReason("URL is null or blank");
            debug.setCountry("UNKNOWN");
            debug.setRegion("UNKNOWN");
            debug.setCity("UNKNOWN");
            return debug;
        }

        try {
            String domain = extractDomain(url);
            debug.setDomain(domain);

            String resolvedIp = resolveIp(domain);
            debug.setResolvedIp(resolvedIp);
            debug.setLoopbackIp(isLoopbackIp(resolvedIp));

            if (resolvedIp == null || resolvedIp.isBlank()) {
                debug.setFallbackReason("DNS resolved empty IP");
                applyUnknown(debug);
                return debug;
            }
            if (debug.isLoopbackIp()) {
                debug.setFallbackReason("DNS resolved loopback IP");
                applyUnknown(debug);
                return debug;
            }

            debug.setTokenPresent(apiKey != null && !apiKey.isBlank());
            String requestUrl = buildIpApiUri(resolvedIp.trim());
            debug.setIpapiRequestUrl(apiKey == null || apiKey.isBlank() ? requestUrl : requestUrl.replace(apiKey, "XXX"));

            ResponseEntity<String> response = restTemplate.exchange(requestUrl, HttpMethod.GET, null, String.class);
            HttpStatusCode statusCode = response.getStatusCode();
            debug.setHttpStatus(statusCode.toString());
            debug.setRawResponse(response.getBody());

            if (!statusCode.is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                debug.setFallbackReason("ipapi response was non-2xx or empty");
                applyUnknown(debug);
                return debug;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            debug.setCountry(firstTextOrUnknown(root, "country_name", "country", "country_code"));
            debug.setRegion(firstTextOrUnknown(root, "region", "region_name"));
            debug.setCity(textOrUnknown(root, "city"));

            if ("UNKNOWN".equals(debug.getRegion())) {
                debug.setFallbackReason("region field missing/blank in ipapi response");
            }
            if ("UNKNOWN".equals(debug.getCountry()) && debug.getFallbackReason() == null) {
                debug.setFallbackReason("country field missing/blank in ipapi response");
            }

            debug.setSuccess(!"UNKNOWN".equals(debug.getCountry()));
            return debug;
        } catch (Exception ex) {
            debug.setFallbackReason(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            applyUnknown(debug);
            return debug;
        }
    }

    private GeoData getRegionByIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return GeoData.unknown();
        }

        try {
            String safeIp = ip.trim();
            if (isPrivateIP(safeIp)) {
                safeIp = "8.8.8.8";
            }

            String url = "https://ipapi.co/" + safeIp + "/json/";
            String response = restTemplate.getForObject(url, String.class);

            System.out.println("IP USED: " + safeIp);
            System.out.println("IPAPI RESPONSE: " + response);

            if (response == null || response.isBlank()) {
                log.error("ipapi response invalid for ip={}", maskIp(safeIp));
                return GeoData.unknown();
            }

            JsonNode root = objectMapper.readTree(response);
            String country = firstTextOrUnknown(root, "country_name", "country", "country_code");
            String region = firstTextOrUnknown(root, "region", "region_name");
            String city = root.has("city") && !root.get("city").isNull()
                    ? root.get("city").asText()
                    : "UNKNOWN";

            if (city == null || city.isBlank()) {
                city = "UNKNOWN";
            }

            if ("UNKNOWN".equals(region)) {
                log.error("Region missing/null in ipapi response for ip={}", maskIp(safeIp));
            }
            if ("UNKNOWN".equals(country)) {
                log.error("Country missing/null in ipapi response for ip={}", maskIp(safeIp));
            }

            return new GeoData(country, region, city);
        } catch (Exception ex) {
            log.warn("ipapi lookup failed for ip={}: {}", maskIp(ip), ex.getMessage());
            return GeoData.unknown();
        }
    }

    private String buildIpApiUri(String ip) {
        String baseUri = UriComponentsBuilder
                .fromUriString(IPAPI_URL)
                .buildAndExpand(ip)
                .encode(StandardCharsets.UTF_8)
                .toUriString();

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUri);

        if (apiKey != null && !apiKey.isBlank()) {
            builder.queryParam("key", apiKey);
        }
        return builder.toUriString();
    }

    private String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }

        String normalized = ip.trim();
        if (normalized.contains(".")) {
            String[] parts = normalized.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".x.x";
            }
        }
        return "[ipv6]";
    }

    private String extractDomain(String url) throws URISyntaxException {
        URI uri = new URI(url);
        String host = uri.getHost();

        if (host == null || host.isBlank()) {
            uri = new URI("https://" + url);
            host = uri.getHost();
        }

        if (host == null || host.isBlank()) {
            log.error("Domain extraction failed for URL={}", url);
            throw new IllegalArgumentException("Unable to extract domain");
        }

        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private String resolveIp(String domain) throws Exception {
        InetAddress inet = InetAddress.getByName(domain);
        return inet.getHostAddress();
    }

    private String textOrUnknown(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return "UNKNOWN";
        }
        return node.asText();
    }

    private String firstTextOrUnknown(JsonNode root, String... fields) {
        for (String field : fields) {
            JsonNode node = root.get(field);
            if (node != null && !node.isNull() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return "UNKNOWN";
    }

    private String normalizeIp(String rawIp) {
        if (rawIp == null || rawIp.isBlank()) {
            return null;
        }

        String[] candidates = rawIp.split(",");
        String fallback = null;

        for (String candidate : candidates) {
            String normalized = normalizeSingleIp(candidate);
            if (normalized == null) {
                continue;
            }

            if (isLoopbackIp(normalized) || isPrivateIp(normalized)) {
                if (fallback == null) {
                    fallback = normalized;
                }
                continue;
            }

            return normalized;
        }

        return fallback;
    }

    private String normalizeSingleIp(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        String ip = rawValue.trim();
        if ("unknown".equalsIgnoreCase(ip)) {
            return null;
        }

        if (ip.startsWith("[") && ip.contains("]")) {
            ip = ip.substring(1, ip.indexOf(']'));
        }

        if (ip.startsWith("::ffff:")) {
            ip = ip.substring(7);
        }

        // Handle IPv4 values that include a port (e.g. 203.0.113.10:51234).
        int colonIndex = ip.lastIndexOf(':');
        if (colonIndex > 0 && ip.contains(".") && ip.indexOf(':') == colonIndex) {
            ip = ip.substring(0, colonIndex);
        }

        if (!isValidIpLiteral(ip)) {
            return null;
        }

        return ip;
    }

    private boolean isValidIpLiteral(String value) {
        if (value == null || value.isBlank() || !value.matches("^[0-9a-fA-F:.]+$")) {
            return false;
        }

        try {
            InetAddress.getByName(value);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isLoopbackIp(String ip) {
        return ip.startsWith("127.") || "0.0.0.0".equals(ip) || "::1".equals(ip);
    }

    private boolean isPrivateIp(String ip) {
        return isPrivateIP(ip);
    }

    private boolean isPrivateIP(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        return ip.startsWith("127.")
                || ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || ip.startsWith("172.16.")
                || "localhost".equalsIgnoreCase(ip)
                || "::1".equals(ip);
    }


    private void applyUnknown(GeoDebugResponse debug) {
        debug.setCountry("UNKNOWN");
        debug.setRegion("UNKNOWN");
        debug.setCity("UNKNOWN");
        debug.setSuccess(false);
    }
}

