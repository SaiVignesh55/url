package com.url.shortener.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.url.shortener.dtos.GeoDebugResponse;
import com.url.shortener.dtos.GeoData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;

@Service
@Slf4j
public class GeoApiService {

    private static final String IP_API_URL = "http://ip-api.com/json/%s";

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

            GeoData geoData = getGeoDataFromIp(resolvedIp);
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
            log.warn("Geo lookup skipped: client IP is missing or invalid");
            return GeoData.unknown();
        }

        if (!isValidPublicIP(normalizedIp)) {
            log.warn("Skipping ip-api lookup for private/invalid ip={}", normalizedIp);
            return GeoData.unknown();
        }

        GeoData geoData = getGeoDataFromIp(normalizedIp);
        log.info("CLIENT IP GEO: ip={} country={} region={} city={}",
                normalizedIp,
                geoData.getCountry(),
                geoData.getRegion(),
                geoData.getCity());
        return geoData;
    }

    public String getCityFromIp(String ip) {
        try {
            String normalized = normalizeIp(ip);
            if (!isValidPublicIP(normalized)) {
                return "UNKNOWN";
            }

            String url = String.format(IP_API_URL, normalized);
            RestTemplate localRestTemplate = new RestTemplate();
            String response = localRestTemplate.getForObject(url, String.class);

            System.out.println("IP-API RAW RESPONSE: " + response);

            JsonNode json = objectMapper.readTree(response);
            String status = json.has("status") ? json.get("status").asText() : "fail";
            if (!"success".equalsIgnoreCase(status)) {
                System.out.println("IP-API FAILED");
                return "UNKNOWN";
            }

            String city = json.has("city") && !json.get("city").isNull()
                    ? json.get("city").asText()
                    : "UNKNOWN";

            System.out.println("PARSED CITY: " + city);
            return city == null || city.isBlank() ? "UNKNOWN" : city;
        } catch (Exception e) {
            e.printStackTrace();
            return "UNKNOWN";
        }
    }

    public String fetchIpApiRawForTest(String ip) {
        try {
            String requestUrl = String.format(IP_API_URL, ip);
            String responseBody = restTemplate.getForObject(requestUrl, String.class);
            return responseBody == null ? "" : responseBody;
        } catch (Exception ex) {
            return "IP-API test call failed: " + ex.getMessage();
        }
    }

    public GeoDebugResponse debugRegionFromUrl(String url) {
        GeoDebugResponse debug = new GeoDebugResponse();
        debug.setUrl(url);
        debug.setSuccess(false);

        if (url == null || url.isBlank()) {
            debug.setFallbackReason("URL is null or blank");
            applyUnknown(debug);
            return debug;
        }

        try {
            String domain = extractDomain(url);
            debug.setDomain(domain);

            String resolvedIp = resolveIp(domain);
            debug.setResolvedIp(resolvedIp);
            debug.setLoopbackIp(isLoopbackIp(resolvedIp));

            if (resolvedIp == null || resolvedIp.isBlank() || !isValidPublicIP(resolvedIp)) {
                debug.setFallbackReason("DNS resolved invalid/private IP");
                applyUnknown(debug);
                return debug;
            }

            String requestUrl = String.format(IP_API_URL, resolvedIp.trim());
            debug.setIpApiRequestUrl(requestUrl);
            debug.setTokenPresent(false);

            String raw = restTemplate.getForObject(requestUrl, String.class);
            debug.setRawResponse(raw);
            debug.setHttpStatus("200 OK");

            if (raw == null || raw.isBlank()) {
                debug.setFallbackReason("ip-api response was empty");
                applyUnknown(debug);
                return debug;
            }

            JsonNode root = objectMapper.readTree(raw);
            String status = root.has("status") ? root.get("status").asText() : "fail";
            if (!"success".equalsIgnoreCase(status)) {
                debug.setFallbackReason("ip-api status=fail: " + (root.has("message") ? root.get("message").asText() : "unknown"));
                applyUnknown(debug);
                return debug;
            }

            debug.setCountry(firstTextOrUnknown(root, "country"));
            debug.setRegion(firstTextOrUnknown(root, "regionName"));
            debug.setCity(firstTextOrUnknown(root, "city"));
            debug.setSuccess(true);
            return debug;
        } catch (Exception ex) {
            debug.setFallbackReason(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            applyUnknown(debug);
            return debug;
        }
    }

    private GeoData getGeoDataFromIp(String ip) {
        if (ip == null || ip.isBlank() || !isValidPublicIP(ip)) {
            return GeoData.unknown();
        }

        try {
            String url = String.format(IP_API_URL, ip.trim());
            String response = restTemplate.getForObject(url, String.class);
            System.out.println("IP-API RAW RESPONSE: " + response);

            if (response == null || response.isBlank()) {
                return GeoData.unknown();
            }

            JsonNode json = objectMapper.readTree(response);
            String status = json.has("status") ? json.get("status").asText() : "fail";
            if (!"success".equalsIgnoreCase(status)) {
                return GeoData.unknown();
            }

            String country = firstTextOrUnknown(json, "country");
            String region = firstTextOrUnknown(json, "regionName");
            String city = firstTextOrUnknown(json, "city");
            return new GeoData(country, region, city == null || city.isBlank() ? "UNKNOWN" : city);
        } catch (Exception ex) {
            log.warn("ip-api lookup failed for ip={}: {}", maskIp(ip), ex.getMessage());
            return GeoData.unknown();
        }
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

    private boolean isValidPublicIP(String ip) {
        return ip != null
                && !ip.startsWith("127.")
                && !ip.startsWith("10.")
                && !ip.startsWith("192.168.")
                && !ip.startsWith("172.");
    }


    private void applyUnknown(GeoDebugResponse debug) {
        debug.setCountry("UNKNOWN");
        debug.setRegion("UNKNOWN");
        debug.setCity("UNKNOWN");
        debug.setSuccess(false);
    }
}

